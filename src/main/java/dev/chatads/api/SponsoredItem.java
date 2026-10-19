package dev.chatads.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SponsoredItem(
        @JsonProperty("product_id") String productId,
        String title,
        String label,
        String reason) {

    public static final String LABEL = "Sponsored";
}
