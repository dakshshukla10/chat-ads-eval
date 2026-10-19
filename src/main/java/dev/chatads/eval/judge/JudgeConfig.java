package dev.chatads.eval.judge;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the judge setup from the environment.
 *
 * <pre>
 * LLM_JUDGE_API_KEY          optional for a local server; a hosted API needs it
 * LLM_JUDGE_BASE_URL         e.g. http://localhost:11434/v1 for Ollama (default https://api.openai.com/v1 when only a key is set)
 * LLM_JUDGE_MODEL            required once the judge is enabled
 * LLM_JUDGE_TIMEOUT_SECONDS  per call, default 120 (small models on a CPU are slow)
 * </pre>
 *
 * The judge is on when a key or a base url is set, and skipped otherwise.
 */
public final class JudgeConfig {

    public static final String KEY_ENV = "LLM_JUDGE_API_KEY";
    public static final String BASE_URL_ENV = "LLM_JUDGE_BASE_URL";
    public static final String MODEL_ENV = "LLM_JUDGE_MODEL";
    public static final String TIMEOUT_ENV = "LLM_JUDGE_TIMEOUT_SECONDS";

    private JudgeConfig() {
    }

    /** Empty means the judge is not configured and its step should be skipped. */
    public static Optional<JudgeClient> fromEnv(Map<String, String> env) {
        String key = blankToNull(env.get(KEY_ENV));
        String baseUrl = blankToNull(env.get(BASE_URL_ENV));
        if (key == null && baseUrl == null) {
            return Optional.empty();
        }
        String model = blankToNull(env.get(MODEL_ENV));
        if (model == null) {
            throw new IllegalStateException(MODEL_ENV + " must be set when the judge is enabled");
        }
        long seconds = env.get(TIMEOUT_ENV) == null ? 120 : Long.parseLong(env.get(TIMEOUT_ENV).strip());
        return Optional.of(new OpenAiCompatibleJudgeClient(
                baseUrl == null ? "https://api.openai.com/v1" : baseUrl, model, key, Duration.ofSeconds(seconds)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
