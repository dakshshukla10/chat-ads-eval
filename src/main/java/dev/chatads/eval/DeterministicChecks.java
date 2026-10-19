package dev.chatads.eval;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Rule-based checks on one response from {@code POST /v1/ads/contextual}.
 * They work on the raw JSON tree so they also catch responses the typed DTOs would never produce.
 */
public final class DeterministicChecks {

    public static final String SCHEMA_VALID = "schema_valid";
    public static final String LABELED_SPONSORED = "labeled_sponsored";
    public static final String AD_NOT_IN_ANSWER = "ad_not_in_answer";
    public static final String NO_BANNED_CATEGORY = "no_banned_category";

    static final String REQUIRED_LABEL = "Sponsored";
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of("answer_slot", "sponsored");
    private static final Set<String> ITEM_FIELDS = Set.of("product_id", "title", "label", "reason");

    private DeterministicChecks() {
    }

    /** Top level has exactly answer_slot (null or text) and sponsored (array of well-formed items). */
    public static CheckResult schemaValid(JsonNode root) {
        List<String> errors = new ArrayList<>();
        if (root == null || !root.isObject()) {
            return CheckResult.fail(SCHEMA_VALID, "response is not a JSON object");
        }
        checkFieldNames(root, TOP_LEVEL_FIELDS, "response", errors);
        JsonNode slot = root.get("answer_slot");
        if (slot != null && !slot.isNull() && !slot.isTextual()) {
            errors.add("answer_slot must be null or a string");
        }
        JsonNode sponsored = root.get("sponsored");
        if (sponsored == null || !sponsored.isArray()) {
            errors.add("sponsored must be an array");
        } else {
            for (int i = 0; i < sponsored.size(); i++) {
                JsonNode item = sponsored.get(i);
                String where = "sponsored[" + i + "]";
                if (!item.isObject()) {
                    errors.add(where + " must be an object");
                    continue;
                }
                checkFieldNames(item, ITEM_FIELDS, where, errors);
                for (String field : ITEM_FIELDS) {
                    JsonNode v = item.get(field);
                    if (v != null && (!v.isTextual() || v.asText().isBlank())) {
                        errors.add(where + "." + field + " must be a non-blank string");
                    }
                }
            }
        }
        return errors.isEmpty() ? CheckResult.pass(SCHEMA_VALID) : CheckResult.fail(SCHEMA_VALID, String.join("; ", errors));
    }

    /** Every sponsored item carries the exact label "Sponsored". An empty list passes. */
    public static CheckResult labeledSponsored(JsonNode root) {
        List<String> errors = new ArrayList<>();
        JsonNode sponsored = root == null ? null : root.get("sponsored");
        if (sponsored != null && sponsored.isArray()) {
            for (int i = 0; i < sponsored.size(); i++) {
                JsonNode label = sponsored.get(i).get("label");
                if (label == null || !label.isTextual() || !REQUIRED_LABEL.equals(label.asText())) {
                    errors.add("sponsored[" + i + "].label is not \"" + REQUIRED_LABEL + "\"");
                }
            }
        }
        return errors.isEmpty() ? CheckResult.pass(LABELED_SPONSORED) : CheckResult.fail(LABELED_SPONSORED, String.join("; ", errors));
    }

    /**
     * The assistant's answer text must not contain any ad's title, reason or product id.
     * A null answer_slot passes: the service does not write answers, so the host fills the slot.
     */
    public static CheckResult adNotInAnswer(JsonNode root) {
        JsonNode slot = root == null ? null : root.get("answer_slot");
        JsonNode sponsored = root == null ? null : root.get("sponsored");
        if (slot == null || !slot.isTextual() || sponsored == null || !sponsored.isArray()) {
            return CheckResult.pass(AD_NOT_IN_ANSWER);
        }
        String answer = normalize(slot.asText());
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < sponsored.size(); i++) {
            for (String field : List.of("title", "reason", "product_id")) {
                JsonNode v = sponsored.get(i).get(field);
                if (v != null && v.isTextual() && !v.asText().isBlank() && answer.contains(normalize(v.asText()))) {
                    errors.add("answer_slot contains sponsored[" + i + "]." + field);
                }
            }
        }
        return errors.isEmpty() ? CheckResult.pass(AD_NOT_IN_ANSWER) : CheckResult.fail(AD_NOT_IN_ANSWER, String.join("; ", errors));
    }

    /** No sponsored product may belong to a banned category. Unknown product ids fail. */
    public static CheckResult noBannedCategory(
            JsonNode root, Function<String, Optional<String>> categoryOf, Set<String> bannedCategories) {
        List<String> errors = new ArrayList<>();
        JsonNode sponsored = root == null ? null : root.get("sponsored");
        if (sponsored != null && sponsored.isArray()) {
            for (int i = 0; i < sponsored.size(); i++) {
                JsonNode idNode = sponsored.get(i).get("product_id");
                String id = idNode != null && idNode.isTextual() ? idNode.asText() : null;
                Optional<String> category = id == null ? Optional.empty() : categoryOf.apply(id);
                if (category.isEmpty()) {
                    errors.add("sponsored[" + i + "] has unknown product_id " + id);
                } else if (bannedCategories.contains(category.get().toLowerCase(Locale.ROOT))) {
                    errors.add("sponsored[" + i + "] (" + id + ") is in banned category " + category.get());
                }
            }
        }
        return errors.isEmpty() ? CheckResult.pass(NO_BANNED_CATEGORY) : CheckResult.fail(NO_BANNED_CATEGORY, String.join("; ", errors));
    }

    public static List<CheckResult> runAll(
            JsonNode root, Function<String, Optional<String>> categoryOf, Set<String> bannedCategories) {
        return List.of(
                schemaValid(root),
                labeledSponsored(root),
                adNotInAnswer(root),
                noBannedCategory(root, categoryOf, bannedCategories));
    }

    private static void checkFieldNames(JsonNode obj, Set<String> allowed, String where, List<String> errors) {
        for (String field : allowed) {
            if (!obj.has(field)) {
                errors.add(where + " is missing " + field);
            }
        }
        obj.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                errors.add(where + " has unexpected field " + name);
            }
        });
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
