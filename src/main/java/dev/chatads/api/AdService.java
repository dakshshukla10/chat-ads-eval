package dev.chatads.api;

import dev.chatads.metrics.AdMetrics;
import dev.chatads.metrics.FallbackReason;
import dev.chatads.policy.PolicyFilter;
import dev.chatads.ranking.Ranker;
import dev.chatads.retrieval.Retriever;
import dev.chatads.retrieval.ScoredProduct;
import java.util.List;
import org.springframework.stereotype.Service;

/** prompt -> retrieve -> policy filter -> rank -> labeled ads. */
@Service
public class AdService {

    static final int CANDIDATES = 10;
    static final int MAX_ADS = 3;

    private final Retriever retriever;
    private final PolicyFilter policy;
    private final Ranker ranker;
    private final AdMetrics metrics;

    public AdService(Retriever retriever, PolicyFilter policy, Ranker ranker, AdMetrics metrics) {
        this.retriever = retriever;
        this.policy = policy;
        this.ranker = ranker;
        this.metrics = metrics;
    }

    /** The assistant's own answer, if the host sent one, goes back in {@code answer_slot} unchanged. */
    public AdResponse contextual(String prompt, String assistantAnswer) {
        metrics.request();
        List<ScoredProduct> candidates = retriever.retrieve(prompt, CANDIDATES);
        List<ScoredProduct> allowed = policy.apply(candidates);
        metrics.policyFiltered(candidates.size() - allowed.size());
        List<ScoredProduct> top = ranker.rank(allowed, MAX_ADS);

        if (top.isEmpty()) {
            metrics.fallback(candidates.isEmpty() ? FallbackReason.NO_MATCH : FallbackReason.POLICY_FILTERED);
        }
        metrics.served(top.size());

        List<SponsoredItem> items = top.stream()
                .map(c -> new SponsoredItem(
                        c.product().id(),
                        c.product().title(),
                        SponsoredItem.LABEL,
                        reason(c)))
                .toList();
        return new AdResponse(assistantAnswer, items);
    }

    /** Vector-only matches have no shared words to quote, so the reason names the category. */
    private static String reason(ScoredProduct c) {
        if (c.matchedTerms().isEmpty()) {
            return "Similar to your request, in " + c.product().category().replace('-', ' ');
        }
        return "Matches your request: " + String.join(", ", c.matchedTerms());
    }
}
