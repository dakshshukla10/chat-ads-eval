package dev.chatads.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chatads.eval.judge.DimensionStats;
import dev.chatads.eval.judge.JudgeDimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pass bars for the eval gate, read from {@code evals/thresholds.json}. The workflow only runs the
 * runner; raising or lowering a bar is a reviewed change to that file.
 */
public final class Thresholds {

    private final JsonNode root;

    private Thresholds(JsonNode root) {
        this.root = root;
    }

    public static Thresholds load(Path file) throws IOException {
        return new Thresholds(new ObjectMapper().readTree(Files.readAllBytes(file)));
    }

    public static Thresholds parse(String json) throws IOException {
        return new Thresholds(new ObjectMapper().readTree(json));
    }

    public double minDeterministicPassRate() {
        return required("deterministic", "min_case_pass_rate");
    }

    public double maxFormatFailureRate() {
        return required("judge", "max_format_failure_rate");
    }

    public double minCalibrationAgreement() {
        return required("calibration", "min_agreement");
    }

    /** Returns one message per bar that was missed; empty means the judge run passes. */
    public List<String> judgeBreaches(Map<JudgeDimension, DimensionStats> stats) {
        List<String> out = new ArrayList<>();
        for (JudgeDimension d : JudgeDimension.values()) {
            DimensionStats s = stats.get(d);
            if (s == null || s.total() == 0) {
                continue;
            }
            JsonNode bar = root.path("judge").path("dimensions").path(d.key());
            if (bar.isMissingNode()) {
                throw new IllegalStateException("thresholds.json has no bar for " + d.key());
            }
            if (s.meanScore() < bar.path("min_mean_score").asDouble()) {
                out.add(String.format("%s mean score %.2f is below %.2f", d.key(), s.meanScore(), bar.path("min_mean_score").asDouble()));
            }
            if (s.passRate() < bar.path("min_pass_rate").asDouble()) {
                out.add(String.format("%s pass rate %.2f is below %.2f", d.key(), s.passRate(), bar.path("min_pass_rate").asDouble()));
            }
            if (s.formatFailureRate() > maxFormatFailureRate()) {
                out.add(String.format("%s format failure rate %.2f is above %.2f", d.key(), s.formatFailureRate(), maxFormatFailureRate()));
            }
        }
        return out;
    }

    private double required(String section, String key) {
        JsonNode n = root.path(section).path(key);
        if (!n.isNumber()) {
            throw new IllegalStateException("thresholds.json is missing " + section + "." + key);
        }
        return n.asDouble();
    }
}
