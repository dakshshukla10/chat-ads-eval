package dev.chatads.retrieval;

import java.util.List;

/** A retrieval candidate: the product, its match score and the prompt terms that matched. */
public record ScoredProduct(Product product, int score, List<String> matchedTerms) {
}
