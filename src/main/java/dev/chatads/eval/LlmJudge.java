package dev.chatads.eval;

/**
 * LLM-as-judge step (relevance, ad-answer separation, safety).
 *
 * <p>STUB. Nothing here calls a model yet; the real judge is planned for Phase 3.
 * The key is read from the {@code LLM_JUDGE_API_KEY} environment variable and is never
 * stored in the repo. With no key the step is skipped, which is what CI does for forks and
 * for repos that have not added the secret.
 */
public final class LlmJudge {

    public static final String KEY_ENV = "LLM_JUDGE_API_KEY";

    private LlmJudge() {
    }

    public static boolean keyPresent() {
        String key = System.getenv(KEY_ENV);
        return key != null && !key.isBlank();
    }

    /** Returns a one-line status for the run log. Never fails the build. */
    public static String run() {
        if (!keyPresent()) {
            return "LLM judge: SKIPPED (" + KEY_ENV + " is not set)";
        }
        return "LLM judge: key found, but the judge is not implemented yet (Phase 3). Nothing was scored.";
    }
}
