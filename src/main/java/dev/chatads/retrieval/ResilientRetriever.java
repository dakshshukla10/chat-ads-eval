package dev.chatads.retrieval;

import dev.chatads.metrics.AdMetrics;
import dev.chatads.metrics.FallbackReason;
import java.time.Duration;
import java.util.List;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tries the primary retriever and falls back to a second one when it throws. After a failure the
 * primary is skipped for {@code cooldown}, so a dead database costs one slow request, not every request.
 * Each request answered by the fallback counts once under {@link FallbackReason#BACKEND_ERROR}.
 */
public class ResilientRetriever implements Retriever {

    private static final Logger log = LoggerFactory.getLogger(ResilientRetriever.class);

    private final Retriever primary;
    private final Retriever fallback;
    private final AdMetrics metrics;
    private final long cooldownNanos;
    private final LongSupplier nanoClock;
    private volatile Long skipPrimaryUntil;

    public ResilientRetriever(Retriever primary, Retriever fallback, AdMetrics metrics, Duration cooldown) {
        this(primary, fallback, metrics, cooldown, System::nanoTime);
    }

    ResilientRetriever(Retriever primary, Retriever fallback, AdMetrics metrics, Duration cooldown, LongSupplier nanoClock) {
        this.primary = primary;
        this.fallback = fallback;
        this.metrics = metrics;
        this.cooldownNanos = cooldown.toNanos();
        this.nanoClock = nanoClock;
    }

    @Override
    public List<ScoredProduct> retrieve(String prompt, int limit) {
        long now = nanoClock.getAsLong();
        Long until = skipPrimaryUntil;
        if (until == null || now - until >= 0) {
            try {
                return primary.retrieve(prompt, limit);
            } catch (RuntimeException e) {
                skipPrimaryUntil = now + cooldownNanos;
                log.warn("Hybrid retrieval failed, using the in-memory keyword retriever: {}", e.toString());
            }
        }
        metrics.fallback(FallbackReason.BACKEND_ERROR);
        return fallback.retrieve(prompt, limit);
    }
}
