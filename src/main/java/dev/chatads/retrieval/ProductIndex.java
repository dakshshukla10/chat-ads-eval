package dev.chatads.retrieval;

import java.util.Collection;
import java.util.List;

/** The two searches the hybrid retriever fuses. Both return best first. */
public interface ProductIndex {

    /** A product and the backend's own score for it (full-text rank or cosine similarity). */
    record Hit(Product product, double score) { }

    /** Full-text search, any of the terms may match. */
    List<Hit> keywordSearch(Collection<String> terms, int limit);

    /** Nearest neighbours by cosine similarity, in [-1, 1]. */
    List<Hit> vectorSearch(float[] query, int limit);
}
