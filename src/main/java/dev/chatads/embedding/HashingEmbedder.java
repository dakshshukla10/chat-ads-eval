package dev.chatads.embedding;

import dev.chatads.retrieval.Tokenizer;
import java.nio.charset.StandardCharsets;

/**
 * Deterministic embedder with no model and no network. Each stemmed word and each character
 * trigram of it is hashed into one of {@code dimensions} buckets (signed, so collisions cancel
 * on average), then the vector is scaled to unit length.
 *
 * <p>The trigrams are what make it useful: "runing shose" still lands near "running shoes".
 * It has no sense of meaning, so "sneakers" and "trainers" are unrelated to it. Use a real
 * embedding provider for that.
 */
public class HashingEmbedder implements Embedder {

    private static final float WORD_WEIGHT = 1.0f;
    private static final float TRIGRAM_WEIGHT = 0.6f;

    private final int dimensions;

    public HashingEmbedder(int dimensions) {
        if (dimensions < 16) {
            throw new IllegalArgumentException("dimensions must be at least 16");
        }
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResult embed(String text) {
        float[] v = new float[dimensions];
        for (String term : Tokenizer.terms(text)) {
            add(v, "w:" + term, WORD_WEIGHT);
            String padded = "#" + term + "#";
            for (int i = 0; i + 3 <= padded.length(); i++) {
                add(v, "t:" + padded.substring(i, i + 3), TRIGRAM_WEIGHT);
            }
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm > 0) {
            float inv = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < v.length; i++) {
                v[i] *= inv;
            }
        }
        return new EmbeddingResult(v, 0, false);
    }

    private void add(float[] v, String feature, float weight) {
        long h = fnv1a(feature);
        int bucket = (int) Long.remainderUnsigned(h, dimensions);
        float sign = ((h >>> 63) == 0) ? 1f : -1f;
        v[bucket] += sign * weight;
    }

    private static long fnv1a(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        return h;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String id() {
        return "hash-v1-" + dimensions;
    }
}
