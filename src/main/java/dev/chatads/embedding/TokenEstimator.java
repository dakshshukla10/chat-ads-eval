package dev.chatads.embedding;

/**
 * Fallback token count for providers that do not report usage: about four characters per token,
 * the usual rule of thumb for English text. Real counts from the provider response always win.
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        return text == null || text.isEmpty() ? 0 : (text.length() + 3) / 4;
    }
}
