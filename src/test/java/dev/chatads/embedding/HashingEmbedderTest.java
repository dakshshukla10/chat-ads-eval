package dev.chatads.embedding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HashingEmbedderTest {

    private final HashingEmbedder embedder = new HashingEmbedder(256);

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    @Test
    void sameTextGivesTheSameUnitVector() {
        float[] a = embedder.embed("running shoes").vector();
        float[] b = embedder.embed("running shoes").vector();
        assertArrayEquals(a, b);
        assertEquals(1.0, cosine(a, a), 1e-5);
        assertEquals(256, a.length);
    }

    @Test
    void aMisspellingStaysCloserThanAnUnrelatedWord() {
        float[] typo = embedder.embed("runing").vector();
        float[] right = embedder.embed("running").vector();
        float[] other = embedder.embed("blender").vector();
        assertTrue(cosine(typo, right) > 0.4);
        assertTrue(cosine(typo, right) > cosine(typo, other) + 0.3);
    }

    @Test
    void synonymsAreNotRelated() {
        // Documented limit of the offline embedder: it matches spelling, not meaning.
        assertTrue(cosine(embedder.embed("sneakers").vector(), embedder.embed("trainers").vector()) < 0.2);
    }

    @Test
    void textWithNoSearchableWordsIsAZeroVector() {
        assertTrue(embedder.embed("???").isZero());
        assertTrue(embedder.embed("the of a").isZero());
        assertFalse(embedder.embed("shoes").isZero());
    }

    @Test
    void offlineEmbeddingReportsNoTokens() {
        EmbeddingResult r = embedder.embed("running shoes");
        assertEquals(0, r.inputTokens());
        assertFalse(r.tokensEstimated());
        assertEquals("hash-v1-256", embedder.id());
    }
}
