package dev.chatads.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.chatads.eval.judge.DimensionStats;
import dev.chatads.eval.judge.Judge;
import dev.chatads.eval.judge.JudgeClient;
import dev.chatads.eval.judge.JudgeConfig;
import dev.chatads.eval.judge.JudgeDimension;
import dev.chatads.eval.judge.Rubric;
import dev.chatads.llm.PriceTable;
import dev.chatads.llm.TokenUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Eval gate entry point. Exit code 0 means every bar in {@code evals/thresholds.json} was met,
 * 1 means a bar was missed or a check failed, 2 means bad arguments or configuration.
 *
 * <pre>
 * Options: --base-url URL      (env EVAL_BASE_URL, default http://localhost:8080)
 *          --dataset PATH      (default evals/prompts.jsonl)
 *          --thresholds PATH   (default evals/thresholds.json)
 *          --rubrics DIR       (default evals/judge)
 *          --report PATH       (deterministic report, default target/eval-report.json)
 *          --judge-report PATH (default target/judge-report.json)
 *          --mode deterministic | judge | calibration | all   (default deterministic)
 * </pre>
 *
 * The judge modes need LLM_JUDGE_BASE_URL or LLM_JUDGE_API_KEY (see {@link JudgeConfig}). Without
 * either they print SKIPPED and exit 0, except under {@code --require-judge}, which exits 2 so a CI
 * job that is meant to run the judge cannot pass by silently skipping it.
 */
public final class EvalRunner {

    private EvalRunner() {
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getenv().getOrDefault("EVAL_BASE_URL", "http://localhost:8080");
        String dataset = "evals/prompts.jsonl";
        String thresholds = "evals/thresholds.json";
        String rubrics = "evals/judge";
        String report = "target/eval-report.json";
        String judgeReport = "target/judge-report.json";
        String mode = "deterministic";
        boolean requireJudge = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--require-judge")) {
                requireJudge = true;
                continue;
            }
            if (i + 1 >= args.length) {
                System.err.println("Missing value for " + args[i]);
                System.exit(2);
            }
            switch (args[i]) {
                case "--base-url" -> baseUrl = args[++i];
                case "--dataset" -> dataset = args[++i];
                case "--thresholds" -> thresholds = args[++i];
                case "--rubrics" -> rubrics = args[++i];
                case "--report" -> report = args[++i];
                case "--judge-report" -> judgeReport = args[++i];
                case "--mode" -> mode = args[++i];
                default -> {
                    System.err.println("Unknown option " + args[i]);
                    System.exit(2);
                }
            }
        }
        if (!List.of("deterministic", "judge", "calibration", "all").contains(mode)) {
            System.err.println("Unknown mode " + mode);
            System.exit(2);
        }

        Thresholds bars = Thresholds.load(Path.of(thresholds));
        List<EvalCase> cases = EvalSet.load(Path.of(dataset));
        ServiceClient service = new ServiceClient(baseUrl);
        boolean ok = true;

        if (mode.equals("deterministic") || mode.equals("all")) {
            DeterministicRun.Outcome d = DeterministicRun.run(service, cases, Path.of(report));
            boolean passed = d.total() > 0 && d.passRate() >= bars.minDeterministicPassRate();
            System.out.printf("Deterministic gate: pass rate %.2f, bar %.2f -> %s%n", d.passRate(), bars.minDeterministicPassRate(), passed ? "PASS" : "FAIL");
            ok &= passed;
        }
        if (!mode.equals("deterministic")) {
            Optional<JudgeClient> client = JudgeConfig.fromEnv(System.getenv());
            if (client.isEmpty()) {
                System.out.println("LLM judge: SKIPPED (" + JudgeConfig.BASE_URL_ENV + " and " + JudgeConfig.KEY_ENV + " are not set)");
                if (requireJudge) {
                    System.exit(2);
                }
            } else {
                ok &= runJudge(client.get(), service, cases, bars, Path.of(rubrics), Path.of(judgeReport), mode);
            }
        }
        System.exit(ok ? 0 : 1);
    }

    private static boolean runJudge(JudgeClient client, ServiceClient service, List<EvalCase> cases, Thresholds bars,
                                    Path rubricDir, Path reportFile, String mode) throws Exception {
        Map<JudgeDimension, Rubric> rubrics = Rubric.loadAll(rubricDir);
        Judge judge = new Judge(client);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode report = mapper.createObjectNode();
        report.put("model", client.model());
        ObjectNode versions = report.putObject("rubric_versions");
        rubrics.forEach((d, r) -> versions.put(d.key(), r.version()));
        TokenUsage usage = TokenUsage.NONE;
        boolean ok = true;

        if (mode.equals("judge") || mode.equals("all")) {
            System.out.println("\nJudge over the eval set, model " + client.model());
            JudgeRun.Outcome out = JudgeRun.run(service, judge, rubrics, cases);
            List<String> breaches = bars.judgeBreaches(out.stats());
            ObjectNode dims = report.putObject("dimensions");
            out.stats().forEach((d, s) -> {
                dims.putObject(d.key()).put("cases", s.total()).put("format_failures", s.formatFailures())
                        .put("mean_score", round(s.meanScore())).put("pass_rate", round(s.passRate()));
                System.out.printf("%-22s cases %-3d mean %.2f  pass rate %.2f  format failures %d%n", d.key(), s.total(), s.meanScore(), s.passRate(), s.formatFailures());
            });
            report.set("items", out.items());
            for (DimensionStats s : out.stats().values()) {
                usage = usage.plus(s.usage());
            }
            breaches.forEach(b -> System.out.println("BELOW BAR: " + b));
            report.putArray("breaches").addAll(breaches.stream().map(mapper.getNodeFactory()::textNode).toList());
            ok &= breaches.isEmpty();
        }

        if (mode.equals("calibration") || mode.equals("all")) {
            System.out.println("\nJudge calibration, model " + client.model());
            CalibrationRun.Outcome out = CalibrationRun.run(judge, rubrics, CalibrationRun.load(rubricDir.resolve("calibration.jsonl")));
            usage = usage.plus(out.usage());
            ObjectNode cal = report.putObject("calibration");
            cal.put("checked", out.overall().checked()).put("agreed", out.overall().agreed()).put("agreement", round(out.overall().rate()));
            out.perDimension().forEach((d, c) -> cal.putObject(d.key()).put("checked", c.checked()).put("agreed", c.agreed()));
            out.missed().forEach(id -> cal.withArray("missed").add(id));
            cal.set("items", out.items());
            boolean passed = out.overall().rate() >= bars.minCalibrationAgreement();
            System.out.printf("Calibration agreement %d/%d = %.2f, bar %.2f -> %s%n", out.overall().agreed(), out.overall().checked(),
                    out.overall().rate(), bars.minCalibrationAgreement(), passed ? "PASS" : "FAIL");
            ok &= passed;
        }

        PriceTable prices = PriceTable.load();
        double cost = prices.cost(client.model(), usage);
        report.putObject("tokens").put("input", usage.inputTokens()).put("output", usage.outputTokens()).put("estimated", usage.estimated());
        report.put("estimated_cost_usd", cost);
        report.put("priced", prices.isPriced(client.model()));
        System.out.printf("Judge tokens: %d in, %d out%s; estimated cost $%.4f%s%n", usage.inputTokens(), usage.outputTokens(),
                usage.estimated() ? " (estimated)" : "", cost, prices.isPriced(client.model()) ? "" : " (model not in the price table, counted as free)");
        if (reportFile.getParent() != null) {
            Files.createDirectories(reportFile.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(reportFile.toFile(), report);
        return ok;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
