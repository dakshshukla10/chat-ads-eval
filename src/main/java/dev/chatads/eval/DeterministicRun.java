package dev.chatads.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.chatads.policy.PolicyFilter;
import dev.chatads.retrieval.Catalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Sends every eval case to the service and applies the deterministic checks to each response. */
final class DeterministicRun {

    private DeterministicRun() {
    }

    record Outcome(int total, int failed) {
        double passRate() {
            return total == 0 ? 0 : (double) (total - failed) / total;
        }
    }

    static Outcome run(ServiceClient service, List<EvalCase> cases, Path reportFile) throws IOException {
        CaseEvaluator evaluator = new CaseEvaluator(Catalog.loadDefault(), PolicyFilter.loadBannedCategories());
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode reportJson = mapper.createObjectNode();
        ArrayNode caseNodes = reportJson.putArray("cases");
        int failedCases = 0;

        for (EvalCase c : cases) {
            List<CheckResult> results;
            try {
                ServiceClient.Reply reply = service.contextual(c);
                results = reply.status() == 200
                        ? evaluator.evaluate(c, reply.body())
                        : List.of(CheckResult.fail("http_status", "expected 200 but got " + reply.status()));
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                results = List.of(CheckResult.fail("http_request", e.getClass().getSimpleName() + ": " + e.getMessage()));
            }

            List<CheckResult> failures = new ArrayList<>(results.stream().filter(r -> !r.passed()).toList());
            if (!failures.isEmpty()) {
                failedCases++;
            }
            System.out.printf("%-4s %-10s %s%n", failures.isEmpty() ? "PASS" : "FAIL", c.id(), c.prompt());
            failures.forEach(f -> System.out.printf("       - %s: %s%n", f.name(), f.detail()));

            ObjectNode node = caseNodes.addObject();
            node.put("id", c.id());
            node.put("passed", failures.isEmpty());
            ArrayNode checks = node.putArray("checks");
            results.forEach(r -> checks.addObject().put("name", r.name()).put("passed", r.passed()).put("detail", r.detail()));
        }

        reportJson.put("total", cases.size());
        reportJson.put("failed", failedCases);
        if (reportFile.getParent() != null) {
            Files.createDirectories(reportFile.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), reportJson);
        System.out.printf("%nDeterministic eval: %d/%d cases passed%n", cases.size() - failedCases, cases.size());
        return new Outcome(cases.size(), failedCases);
    }
}
