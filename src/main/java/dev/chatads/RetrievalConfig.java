package dev.chatads;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.chatads.embedding.Embedder;
import dev.chatads.embedding.HashingEmbedder;
import dev.chatads.embedding.HttpEmbedder;
import dev.chatads.embedding.MeteredEmbedder;
import dev.chatads.llm.PriceTable;
import dev.chatads.metrics.LlmUsageMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import dev.chatads.metrics.AdMetrics;
import dev.chatads.retrieval.Catalog;
import dev.chatads.retrieval.HybridRetriever;
import dev.chatads.retrieval.KeywordRetriever;
import dev.chatads.retrieval.PostgresProductIndex;
import dev.chatads.retrieval.ResilientRetriever;
import dev.chatads.retrieval.Retriever;
import java.time.Duration;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Chooses the retriever. With no database url the in-memory keyword retriever answers alone.
 * With one, hybrid search runs on Postgres and the keyword retriever stands behind it as the fallback.
 */
@Configuration
public class RetrievalConfig {

    private static final Logger log = LoggerFactory.getLogger(RetrievalConfig.class);

    @Bean
    KeywordRetriever keywordRetriever(Catalog catalog) {
        return new KeywordRetriever(catalog);
    }

    @Bean
    LlmUsageMetrics llmUsageMetrics(MeterRegistry registry) {
        return new LlmUsageMetrics(registry, PriceTable.load());
    }

    @Bean
    Embedder embedder(
            LlmUsageMetrics usage,
            @Value("${chatads.embedding.provider}") String provider,
            @Value("${chatads.embedding.dimensions}") int dimensions,
            @Value("${chatads.embedding.base-url}") String baseUrl,
            @Value("${chatads.embedding.model}") String model,
            @Value("${chatads.embedding.api-key}") String apiKey) {
        HttpEmbedder.Flavor flavor = switch (provider.toLowerCase()) {
            // The offline embedder calls no model, so there is nothing to meter.
            case "hash" -> null;
            case "openai" -> HttpEmbedder.Flavor.OPENAI;
            case "ollama" -> HttpEmbedder.Flavor.OLLAMA;
            default -> throw new IllegalArgumentException("chatads.embedding.provider must be hash, openai or ollama, got " + provider);
        };
        if (flavor == null) {
            return new HashingEmbedder(dimensions);
        }
        return new MeteredEmbedder(new HttpEmbedder(flavor, baseUrl, model, apiKey, dimensions, Duration.ofSeconds(5)), model, usage);
    }

    @Bean
    @ConditionalOnExpression("!'${chatads.db.url:}'.isEmpty()")
    DataSource dataSource(
            @Value("${chatads.db.url}") String url,
            @Value("${chatads.db.user}") String user,
            @Value("${chatads.db.password}") String password) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(4);
        // A dead database should cost a second, not Hikari's default thirty.
        cfg.setConnectionTimeout(1000);
        // Do not fail startup when Postgres is not up yet; the first request retries.
        cfg.setInitializationFailTimeout(-1);
        return new HikariDataSource(cfg);
    }

    @Bean
    @ConditionalOnExpression("!'${chatads.db.url:}'.isEmpty()")
    PostgresProductIndex productIndex(DataSource dataSource, Catalog catalog, Embedder embedder) {
        return new PostgresProductIndex(new JdbcTemplate(dataSource), catalog, embedder);
    }

    @Bean
    @ConditionalOnExpression("!'${chatads.db.url:}'.isEmpty()")
    ApplicationRunner loadCatalog(PostgresProductIndex index) {
        return args -> {
            try {
                index.ensureReady();
            } catch (RuntimeException e) {
                log.warn("Postgres is not ready yet, serving from the keyword retriever until it is: {}", e.toString());
            }
        };
    }

    @Bean
    Retriever retriever(
            KeywordRetriever keyword,
            ObjectProvider<PostgresProductIndex> index,
            Embedder embedder,
            AdMetrics metrics,
            @Value("${chatads.retrieval.candidates}") int candidates,
            @Value("${chatads.retrieval.rrf-k}") int rrfK,
            @Value("${chatads.retrieval.min-similarity}") double minSimilarity,
            @Value("${chatads.retrieval.vector-relative-cutoff}") double relativeCutoff,
            @Value("${chatads.retrieval.fallback-cooldown}") Duration cooldown) {
        PostgresProductIndex postgres = index.getIfAvailable();
        if (postgres == null) {
            return keyword;
        }
        Retriever hybrid = new HybridRetriever(postgres, embedder, candidates, rrfK, minSimilarity, relativeCutoff);
        return new ResilientRetriever(hybrid, keyword, metrics, cooldown);
    }
}
