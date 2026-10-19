package dev.chatads.embedding;

/** Turns text into a fixed-size vector for the pgvector side of retrieval. */
public interface Embedder {

    EmbeddingResult embed(String text);

    int dimensions();

    /**
     * Names the model and settings. It is stored next to each catalog row, so changing the
     * embedder re-embeds the catalog on the next start.
     */
    String id();
}
