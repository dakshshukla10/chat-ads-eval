package dev.chatads.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Business metrics. Request latency comes from the built-in
 * {@code http.server.requests} timer (percentile histogram enabled in application.yml).
 */
@Component
public class AdMetrics {

    private final Counter requests;
    private final Counter served;
    private final Counter fallback;
    private final Counter policyFiltered;

    public AdMetrics(MeterRegistry registry) {
        this.requests = Counter.builder("chatads.requests").description("Contextual ad requests").register(registry);
        this.served = Counter.builder("chatads.sponsored.served").description("Sponsored items returned").register(registry);
        this.fallback = Counter.builder("chatads.fallback")
                .description("Requests answered with no ad").tag("reason", "no_match").register(registry);
        this.policyFiltered = Counter.builder("chatads.policy.filtered")
                .description("Candidates removed by the policy filter").register(registry);
        // Registered now so the dashboard panel exists; nothing increments these until an LLM is wired in (Phase 3).
        Counter.builder("chatads.llm.tokens").tag("direction", "input").register(registry);
        Counter.builder("chatads.llm.tokens").tag("direction", "output").register(registry);
    }

    public void request() {
        requests.increment();
    }

    public void served(int count) {
        served.increment(count);
    }

    public void fallback() {
        fallback.increment();
    }

    public void policyFiltered(int count) {
        policyFiltered.increment(count);
    }
}
