package dev.chatads.retrieval;

import java.util.List;

/**
 * A retrieval candidate: the product, its score and the prompt terms that matched.
 * The score is only comparable within one retriever (keyword points, or a fused rank score).
 */
public record ScoredProduct(Product product, double score, List<String> matchedTerms) {
}
