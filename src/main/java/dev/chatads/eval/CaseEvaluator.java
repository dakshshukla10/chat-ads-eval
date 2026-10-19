package dev.chatads.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chatads.retrieval.Catalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Runs every deterministic check, plus the expected-category check, on one response body. */
public class CaseEvaluator {

    public static final String EXPECTED_CATEGORY = "expected_category";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Catalog catalog;
    private final Set<String> banned;

    public CaseEvaluator(Catalog catalog, Set<String> banned) {
        this.catalog = catalog;
        this.banned = banned;
    }

    public List<CheckResult> evaluate(EvalCase evalCase, String responseBody) {
        JsonNode root;
        try {
            root = MAPPER.readTree(responseBody);
        } catch (Exception e) {
            return List.of(CheckResult.fail(DeterministicChecks.SCHEMA_VALID, "body is not valid JSON"));
        }
        List<CheckResult> results = new ArrayList<>(DeterministicChecks.runAll(root, catalog::categoryOf, banned));
        results.add(expectedCategory(evalCase, root));
        return results;
    }

    /**
     * Relevance proxy: the top sponsored item must be in an expected category; if none is
     * expected, nothing may be shown. Meaningful only because the catalog and set are synthetic.
     */
    CheckResult expectedCategory(EvalCase evalCase, JsonNode root) {
        JsonNode sponsored = root.get("sponsored");
        boolean none = sponsored == null || !sponsored.isArray() || sponsored.isEmpty();
        if (evalCase.expectedCategories().isEmpty()) {
            return none ? CheckResult.pass(EXPECTED_CATEGORY)
                    : CheckResult.fail(EXPECTED_CATEGORY, "expected no ad but got " + sponsored.size());
        }
        if (none) {
            return CheckResult.fail(EXPECTED_CATEGORY, "expected one of " + evalCase.expectedCategories() + " but got no ad");
        }
        JsonNode id = sponsored.get(0).get("product_id");
        String category = id == null ? null : catalog.categoryOf(id.asText()).orElse(null);
        if (category != null && evalCase.expectedCategories().contains(category)) {
            return CheckResult.pass(EXPECTED_CATEGORY);
        }
        return CheckResult.fail(EXPECTED_CATEGORY,
                "top ad category " + category + " not in " + evalCase.expectedCategories());
    }
}
