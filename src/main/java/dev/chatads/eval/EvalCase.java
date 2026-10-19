package dev.chatads.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * One line of {@code evals/prompts.jsonl}. An empty {@code expectedCategories} means
 * "no ad should be shown for this prompt".
 *
 * @param assistantAnswer optional host answer sent along with the prompt, so the separation checks have text to look at
 * @param tags free labels such as {@code misspelling}; {@code hybrid_only} marks a case keyword matching cannot answer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalCase(
        String id,
        String prompt,
        @JsonProperty("expected_categories") List<String> expectedCategories,
        @JsonProperty("assistant_answer") String assistantAnswer,
        List<String> tags) {

    public EvalCase {
        tags = tags == null ? List.of() : tags;
    }
}
