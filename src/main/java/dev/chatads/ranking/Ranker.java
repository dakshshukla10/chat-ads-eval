package dev.chatads.ranking;

import dev.chatads.retrieval.ScoredProduct;
import java.util.Comparator;
import java.util.List;

/** Orders policy-approved candidates and keeps the top few. */
public class Ranker {

    public List<ScoredProduct> rank(List<ScoredProduct> candidates, int max) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble(ScoredProduct::score).reversed()
                        .thenComparing(c -> c.product().id()))
                .limit(max)
                .toList();
    }
}
