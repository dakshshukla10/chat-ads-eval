package dev.chatads.llm;

/**
 * Tokens for one model call.
 *
 * @param estimated true when the provider sent no usage block and the counts come from
 *     {@link dev.chatads.embedding.TokenEstimator}
 */
public record TokenUsage(int inputTokens, int outputTokens, boolean estimated) {

    public static final TokenUsage NONE = new TokenUsage(0, 0, false);

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens,
                estimated || other.estimated);
    }
}
