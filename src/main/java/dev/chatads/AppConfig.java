package dev.chatads;

import dev.chatads.policy.PolicyFilter;
import dev.chatads.ranking.Ranker;
import dev.chatads.retrieval.Catalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    Catalog catalog() {
        return Catalog.loadDefault();
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
