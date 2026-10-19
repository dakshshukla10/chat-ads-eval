package dev.chatads.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.chatads.policy.PolicyFilter;
import dev.chatads.retrieval.Catalog;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Eval gate entry point. Sends every prompt in the eval set to a running service,
 * applies the deterministic checks and exits 1 if anything fails.
 *
 * <pre>
 * Options: --base-url URL (env EVAL_BASE_URL, default http://localhost:8080)
 *          --dataset PATH (default evals/prompts.jsonl)
 *          --report PATH  (default target/eval-report.json)
 *          --mode deterministic|judge (default deterministic)
 * </pre>
 */
public final class EvalRunner {

    private EvalRunner() {
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getenv().getOrDefault("EVAL_BASE_URL", "http://localhost:8080");
        String dataset = "evals/prompts.jsonl";
        String report = "target/eval-report.json";
        String mode = "deterministic";
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--base-url" -> baseUrl = args[i + 1];
                case "--dataset" -> dataset = args[i + 1];
                case "--report" -> report = args[i + 1];
                case "--mode" -> mode = args[i + 1];
                default -> {
                    System.err.println("Unknown option " + args[i]);
                    System.exit(2);
                }
            }
        }

        if ("judge".equals(mode)) {
            System.out.println(LlmJudge.run());
            return;
        }
        if (!"deterministic".equals(mode)) {
            System.err.println("Unknown mode " + mode);
            System.exit(2);
        }
        System.exit(runDeterministic(baseUrl, Path.of(dataset), Path.of(report)) ? 0 : 1);
    }

    static ObjectNode requestBody(ObjectMapper mapper, EvalCase c) {
        ObjectNode body = mapper.createObjectNode().put("prompt", c.prompt());
        if (c.assistantAnswer() != null) {
            body.put("assistant_answer", c.assistantAnswer());
        }
        return body;
    }

    static boolean runDeterministic(String baseUrl, Path dataset, Path reportFile) throws Exception {
        List<EvalCase> cases = EvalSet.load(dataset);
        CaseEvaluator evaluator = new CaseEvaluator(Catalog.loadDefault(), PolicyFilter.loadBannedCategories());
        ObjectMapper mapper = new ObjectMapper();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        ObjectNode reportJson = mapper.createObjectNode();
        ArrayNode caseNodes = reportJson.putArray("cases");
        int failedCases = 0;

        for (EvalCase c : cases) {
            List<CheckResult> results;
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/ads/contextual"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody(mapper, c))))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                results = resp.statusCode() == 200
                        ? evaluator.evaluate(c, resp.body())
                        : List.of(CheckResult.fail("http_status", "expected 200 but got " + resp.statusCode()));
            } catch (Exception e) {
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
        System.out.println(LlmJudge.run());
        return failedCases == 0 && !cases.isEmpty();
    }
}
