package dev.chatads.eval.judge;

import dev.chatads.llm.TokenUsage;

/**
 * Outcome of judging one dimension of one case. {@code score} is null exactly when the model's
 * reply could not be read after the retry, and {@code formatError} says why.
 */
public record JudgeResult(JudgeDimension dimension, Integer score, String reason, String formatError, TokenUsage usage) {

    public boolean formatFailure() {
        return score == null;
    }
}
