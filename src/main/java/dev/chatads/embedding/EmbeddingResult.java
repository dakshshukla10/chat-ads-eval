package dev.chatads.embedding;

/**
 * @param inputTokens tokens the provider billed for the input; 0 for the offline embedder
 * @param tokensEstimated true when the provider sent no usage block and the count is {@link TokenEstimator}'s guess
 */
public record EmbeddingResult(float[] vector, int inputTokens, boolean tokensEstimated) {

    public boolean isZero() {
        for (float v : vector) {
            if (v != 0f) {
                return false;
            }
        }
        return true;
    }
}
