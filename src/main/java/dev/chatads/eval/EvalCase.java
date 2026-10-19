package dev.chatads.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One line of {@code evals/prompts.jsonl}. An empty {@code expectedCategories} means
 * "no ad should be shown for this prompt".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalCase(
        String id,
        String prompt,
        @JsonProperty("expected_categories") List<String> expectedCategories) {
}
