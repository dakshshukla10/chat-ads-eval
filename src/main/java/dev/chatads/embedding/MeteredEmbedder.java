package dev.chatads.embedding;

import dev.chatads.llm.TokenUsage;
import dev.chatads.metrics.LlmUsageMetrics;

/** Reports every real embedding call's tokens to the metrics. */
public class MeteredEmbedder implements Embedder {

    private final Embedder delegate;
    private final String model;
    private final LlmUsageMetrics metrics;

    public MeteredEmbedder(Embedder delegate, String model, LlmUsageMetrics metrics) {
        this.delegate = delegate;
        this.model = model;
        this.metrics = metrics;
    }

    @Override
    public EmbeddingResult embed(String text) {
        EmbeddingResult r = delegate.embed(text);
        metrics.record("embedding", model, new TokenUsage(r.inputTokens(), 0, r.tokensEstimated()));
        return r;
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    @Override
    public String id() {
        return delegate.id();
    }
}
