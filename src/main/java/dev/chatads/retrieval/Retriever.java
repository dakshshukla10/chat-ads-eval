package dev.chatads.retrieval;

import java.util.List;

public interface Retriever {
    /** Returns candidates for the prompt, best first. Policy filtering happens later. */
    List<ScoredProduct> retrieve(String prompt, int limit);
}
