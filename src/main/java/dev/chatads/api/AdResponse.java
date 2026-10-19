package dev.chatads.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * {@code answer_slot} belongs to the host assistant: this service never writes an answer. It returns
 * the host's {@code assistant_answer} untouched, or null when the host sent none. Ads live only in
 * {@code sponsored}.
 */
public record AdResponse(
        @JsonProperty("answer_slot") String answerSlot,
        List<SponsoredItem> sponsored) {
}
