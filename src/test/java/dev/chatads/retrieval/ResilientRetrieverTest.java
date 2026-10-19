package dev.chatads.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.chatads.metrics.AdMetrics;
import dev.chatads.metrics.FallbackReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ResilientRetrieverTest {

    private static final List<ScoredProduct> PRIMARY = List.of(new ScoredProduct(new Product("a", "A", "c", List.of()), 1, List.of()));
    private static final List<ScoredProduct> BACKUP = List.of(new ScoredProduct(new Product("b", "B", "c", List.of()), 2, List.of()));

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AdMetrics metrics = new AdMetrics(registry);
    private final AtomicLong clock = new AtomicLong(1_000);
    private final AtomicInteger primaryCalls = new AtomicInteger();

    private double fallbacks(FallbackReason reason) {
        return registry.get("chatads.fallback").tag("reason", reason.tag()).counter().count();
    }

    @Test
    void healthyPrimaryIsUsedAndNothingIsCounted() {
        var r = new ResilientRetriever((p, l) -> PRIMARY, (p, l) -> BACKUP, metrics, Duration.ofSeconds(5), clock::get);
        assertSame(PRIMARY, r.retrieve("x", 3));
        assertEquals(0, fallbacks(FallbackReason.BACKEND_ERROR));
    }

    @Test
    void aFailureFallsBackAndCountsOnce() {
        var r = new ResilientRetriever((p, l) -> { throw new IllegalStateException("db down"); }, (p, l) -> BACKUP,
                metrics, Duration.ofSeconds(5), clock::get);
        assertSame(BACKUP, r.retrieve("x", 3));
        assertEquals(1, fallbacks(FallbackReason.BACKEND_ERROR));
    }

    @Test
    void thePrimaryIsSkippedDuringTheCooldownAndRetriedAfterIt() {
        boolean[] healthy = {false};
        Retriever flaky = (p, l) -> {
            primaryCalls.incrementAndGet();
            if (!healthy[0]) {
                throw new IllegalStateException("db down");
            }
            return PRIMARY;
        };
        var r = new ResilientRetriever(flaky, (p, l) -> BACKUP, metrics, Duration.ofSeconds(5), clock::get);

        r.retrieve("x", 3);
        r.retrieve("x", 3);
        assertEquals(1, primaryCalls.get(), "second request must not touch the dead primary");
        assertEquals(2, fallbacks(FallbackReason.BACKEND_ERROR));

        healthy[0] = true;
        clock.addAndGet(Duration.ofSeconds(6).toNanos());
        assertSame(PRIMARY, r.retrieve("x", 3));
        assertEquals(2, primaryCalls.get());
    }
}
