package dev.chatads.retrieval;

import java.util.List;

/** A sponsored product in the (synthetic) catalog. */
public record Product(String id, String title, String category, List<String> keywords) {
}
