package dev.chatads.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.chatads.embedding.Embedder;
import dev.chatads.embedding.EmbeddingResult;
import dev.chatads.embedding.MeteredEmbedder;
import dev.chatads.llm.PriceTable;
import dev.chatads.llm.TokenUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class LlmUsageMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LlmUsageMetrics metrics = new LlmUsageMetrics(registry, PriceTable.load());

    private double tokens(String direction, String model, String source) {
        return registry.get("chatads.llm.tokens").tag("direction", direction).tag("model", model)
                .tag("source", source).counter().count();
    }

    @Test
    void providerCountsAndEstimatesLandInSeparateSeries() {
        metrics.record("embedding", "text-embedding-3-small", new TokenUsage(500, 0, false));
        metrics.record("embedding", "text-embedding-3-small", new TokenUsage(40, 0, true));

        assertEquals(500, tokens("input", "text-embedding-3-small", "provider"));
        assertEquals(40, tokens("input", "text-embedding-3-small", "estimate"));
    }

    @Test
    void costComesFromThePriceTable() {
        metrics.record("embedding", "text-embedding-3-small", new TokenUsage(1_000_000, 0, false));
        double usd = registry.get("chatads.llm.cost.usd").tag("model", "text-embedding-3-small").counter().count();
        assertEquals(0.02, usd, 1e-9);
    }

    @Test
    void meteredEmbedderReportsEachCall() {
        Embedder fake = new Embedder() {
            @Override
            public EmbeddingResult embed(String text) {
                return new EmbeddingResult(new float[] {1f}, 9, false);
            }

            @Override
            public int dimensions() {
                return 1;
            }

            @Override
            public String id() {
                return "fake";
            }
        };
        var metered = new MeteredEmbedder(fake, "local-model", metrics);
        metered.embed("a");
        metered.embed("b");
        assertEquals(18, tokens("input", "local-model", "provider"));
    }
}
