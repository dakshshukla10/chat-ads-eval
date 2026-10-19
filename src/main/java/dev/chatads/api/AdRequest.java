package dev.chatads.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @param assistantAnswer optional text the host assistant has already written for the shopper. It is
 *     handed back untouched in {@code answer_slot} and never used to pick ads.
 */
public record AdRequest(String prompt, @JsonProperty("assistant_answer") String assistantAnswer) {
}
