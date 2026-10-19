package dev.chatads.retrieval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal rank fusion: a document's score is the sum of {@code 1 / (k + rank)} over every
 * list it appears in, with rank starting at 1. It only looks at positions, so a full-text rank
 * and a cosine similarity can be merged without putting them on one scale.
 */
public final class ReciprocalRankFusion {

    private ReciprocalRankFusion() {
    }

    public static Map<String, Double> fuse(List<List<String>> rankedLists, int k) {
        Map<String, Double> scores = new HashMap<>();
        for (List<String> list : rankedLists) {
            for (int i = 0; i < list.size(); i++) {
                scores.merge(list.get(i), 1.0 / (k + i + 1), Double::sum);
            }
        }
        return scores;
    }
}
