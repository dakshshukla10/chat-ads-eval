package dev.chatads.metrics;

import dev.chatads.llm.PriceTable;
import dev.chatads.llm.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Counts tokens and estimated dollars for model calls the service makes itself (today, embedding
 * the shopper's prompt and the catalog when a real embedding provider is configured).
 *
 * <p>{@code chatads.llm.tokens} is tagged by direction, operation, model and source. Source is
 * {@code provider} when the response reported usage and {@code estimate} when the count is a guess.
 */
public class LlmUsageMetrics {

    private final MeterRegistry registry;
    private final PriceTable prices;

    public LlmUsageMetrics(MeterRegistry registry, PriceTable prices) {
        this.registry = registry;
        this.prices = prices;
    }

    public void record(String operation, String model, TokenUsage usage) {
        String source = usage.estimated() ? "estimate" : "provider";
        count("input", operation, model, source, usage.inputTokens());
        count("output", operation, model, source, usage.outputTokens());
        double dollars = prices.cost(model, usage);
        Counter.builder("chatads.llm.cost.usd")
                .description("Estimated model spend in US dollars, from the price table")
                .tag("operation", operation).tag("model", model)
                .register(registry).increment(dollars);
    }

    private void count(String direction, String operation, String model, String source, int tokens) {
        Counter.builder("chatads.llm.tokens")
                .description("Tokens sent to and received from models")
                .tag("direction", direction).tag("operation", operation)
                .tag("model", model).tag("source", source)
                .register(registry).increment(tokens);
    }
}
