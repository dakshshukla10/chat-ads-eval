package dev.chatads.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Business metrics. Request latency comes from the built-in
 * {@code http.server.requests} timer (percentile histogram enabled in application.yml).
 */
@Component
public class AdMetrics {

    private final Counter requests;
    private final Counter served;
    private final Counter policyFiltered;
    private final Map<FallbackReason, Counter> fallbacks = new EnumMap<>(FallbackReason.class);

    public AdMetrics(MeterRegistry registry) {
        this.requests = Counter.builder("chatads.requests").description("Contextual ad requests").register(registry);
        this.served = Counter.builder("chatads.sponsored.served").description("Sponsored items returned").register(registry);
        this.policyFiltered = Counter.builder("chatads.policy.filtered")
                .description("Candidates removed by the policy filter").register(registry);
        // Every reason is registered up front so rate() has a series to read before the first failure.
        for (FallbackReason reason : FallbackReason.values()) {
            fallbacks.put(reason, Counter.builder("chatads.fallback")
                    .description("Requests that left the normal path, by reason")
                    .tag("reason", reason.tag()).register(registry));
        }
    }

    public void request() {
        requests.increment();
    }

    public void served(int count) {
        served.increment(count);
    }

    public void fallback(FallbackReason reason) {
        fallbacks.get(reason).increment();
    }

    public void policyFiltered(int count) {
        policyFiltered.increment(count);
    }
}
