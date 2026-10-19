package dev.chatads.retrieval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chatads.metrics.AdMetrics;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Caches retrieval candidates so policy filtering still runs against the current catalog. */
public final class CachingRetriever implements Retriever {

    private static final Logger log = LoggerFactory.getLogger(CachingRetriever.class);
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final long WARN_INTERVAL_NANOS = Duration.ofSeconds(30).toNanos();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Candidate>> CANDIDATES = new TypeReference<>() { };

    private record Candidate(String id, double score, List<String> matched) { }

    interface Store {
        String get(String key);

        void set(String key, String value, Duration ttl);
    }

    private final Retriever delegate;
    private final Catalog catalog;
    private final Store store;
    private final String embedderId;
    private final Duration ttl;
    private final AdMetrics metrics;
    private Long lastWarning;

    public CachingRetriever(Retriever delegate, Catalog catalog, StringRedisTemplate redis,
                            String embedderId, Duration ttl, AdMetrics metrics) {
        this(delegate, catalog, new Store() {
            @Override
            public String get(String key) {
                return redis.opsForValue().get(key);
            }

            @Override
            public void set(String key, String value, Duration ttl) {
                redis.opsForValue().set(key, value, ttl);
            }
        }, embedderId, ttl, metrics);
    }

    CachingRetriever(Retriever delegate, Catalog catalog, Store store,
                     String embedderId, Duration ttl, AdMetrics metrics) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Cache TTL must be positive");
        }
        this.delegate = delegate;
        this.catalog = catalog;
        this.store = store;
        this.embedderId = embedderId;
        this.ttl = ttl;
        this.metrics = metrics;
    }

    @Override
    public List<ScoredProduct> retrieve(String prompt, int limit) {
        String key = key(embedderId, prompt, limit);
        try {
            String value = store.get(key);
            if (value != null) {
                List<ScoredProduct> cached = restore(value);
                if (cached != null) {
                    metrics.cacheHit();
                    return cached;
                }
            }
        } catch (RuntimeException | JsonProcessingException e) {
            cacheError(e);
            return delegate.retrieve(prompt, limit);
        }

        metrics.cacheMiss();
        // A delegate failure must reach the outer fallback without writing degraded results.
        List<ScoredProduct> candidates = delegate.retrieve(prompt, limit);
        try {
            List<Candidate> value = candidates.stream()
                    .map(c -> new Candidate(c.product().id(), c.score(), c.matchedTerms())).toList();
            store.set(key, JSON.writeValueAsString(value), ttl);
        } catch (RuntimeException | JsonProcessingException e) {
            cacheError(e);
        }
        return candidates;
    }

    private List<ScoredProduct> restore(String value) throws JsonProcessingException {
        List<ScoredProduct> restored = new ArrayList<>();
        for (Candidate candidate : JSON.readValue(value, CANDIDATES)) {
            Product product = catalog.find(candidate.id()).orElse(null);
            if (product == null) {
                return null;
            }
            if (!Double.isFinite(candidate.score()) || candidate.matched() == null) {
                throw new IllegalArgumentException("Invalid cached candidate");
            }
            restored.add(new ScoredProduct(product, candidate.score(), List.copyOf(candidate.matched())));
        }
        return List.copyOf(restored);
    }

    static String key(String embedderId, String prompt, int limit) {
        String normalized = SEPARATORS.matcher(prompt.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        String input = embedderId + "|" + limit + "|" + normalized;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return "chatads:candidates:v1:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private synchronized void cacheError(Exception error) {
        metrics.cacheError();
        long now = System.nanoTime();
        if (lastWarning == null || now - lastWarning >= WARN_INTERVAL_NANOS) {
            lastWarning = now;
            log.warn("Candidate cache failed, continuing without cache: {}", error.getClass().getSimpleName());
        }
    }
}
