package dev.chatads;

import dev.chatads.policy.PolicyFilter;
import dev.chatads.ranking.Ranker;
import dev.chatads.retrieval.Catalog;
import dev.chatads.retrieval.KeywordRetriever;
import dev.chatads.retrieval.Retriever;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    Catalog catalog() {
        return Catalog.loadDefault();
    }

    /** Phase 1 stub. Phase 2 replaces this with hybrid keyword + vector search. */
    @Bean
    Retriever retriever(Catalog catalog) {
        return new KeywordRetriever(catalog);
    }

    @Bean
    PolicyFilter policyFilter() {
        return PolicyFilter.withDefaults();
    }

    @Bean
    Ranker ranker() {
        return new Ranker();
    }
}
