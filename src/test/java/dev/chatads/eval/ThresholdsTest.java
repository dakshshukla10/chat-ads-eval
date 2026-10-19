package dev.chatads.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chatads.eval.judge.DimensionStats;
import dev.chatads.eval.judge.JudgeDimension;
import dev.chatads.eval.judge.JudgeResult;
import dev.chatads.llm.TokenUsage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ThresholdsTest {

    private static DimensionStats stats(JudgeDimension d, Integer... scores) {
        DimensionStats s = DimensionStats.EMPTY;
        for (Integer score : scores) {
            s = s.add(new JudgeResult(d, score, score == null ? null : "r", score == null ? "bad" : null, new TokenUsage(10, 2, false)));
        }
        return s;
    }

    private final Thresholds bars = loadRepoThresholds();

    private static Thresholds loadRepoThresholds() {
        try {
            return Thresholds.load(Path.of("evals/thresholds.json"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theCommittedFileHasEveryBarTheRunnerReads() {
        assertTrue(bars.minDeterministicPassRate() > 0);
        assertTrue(bars.maxFormatFailureRate() >= 0);
        assertTrue(bars.minCalibrationAgreement() > 0);
        Map<JudgeDimension, DimensionStats> perfect = Map.of(
                JudgeDimension.RELEVANCE, stats(JudgeDimension.RELEVANCE, 5, 5, 5),
                JudgeDimension.AD_ANSWER_SEPARATION, stats(JudgeDimension.AD_ANSWER_SEPARATION, 5, 5, 5),
                JudgeDimension.SAFETY, stats(JudgeDimension.SAFETY, 5, 5, 5));
        assertEquals(List.of(), bars.judgeBreaches(perfect));
    }

    @Test
    void aLowMeanAndALowPassRateEachBreachTheirOwnBar() throws Exception {
        Thresholds t = Thresholds.parse("""
                {"deterministic":{"min_case_pass_rate":1.0},
                 "judge":{"max_format_failure_rate":0.2,"dimensions":{
                   "relevance":{"min_mean_score":4.0,"min_pass_rate":0.5},
                   "ad_answer_separation":{"min_mean_score":1,"min_pass_rate":0},
                   "safety":{"min_mean_score":1,"min_pass_rate":0}}},
                 "calibration":{"min_agreement":0.7}}""");
        List<String> breaches = t.judgeBreaches(Map.of(JudgeDimension.RELEVANCE, stats(JudgeDimension.RELEVANCE, 3, 3, 4, 3)));
        assertEquals(2, breaches.size());
        assertTrue(breaches.get(0).startsWith("relevance mean score 3.25 is below 4.00"));
        assertTrue(breaches.get(1).startsWith("relevance pass rate 0.25 is below 0.50"));
    }

    @Test
    void formatFailuresStayInTheDenominator() throws Exception {
        Thresholds t = Thresholds.load(Path.of("evals/thresholds.json"));
        DimensionStats s = stats(JudgeDimension.SAFETY, 5, 5, null, null, null);
        assertEquals(0.4, s.passRate(), 1e-9);
        assertEquals(0.6, s.formatFailureRate(), 1e-9);
        assertTrue(t.judgeBreaches(Map.of(JudgeDimension.SAFETY, s)).stream().anyMatch(b -> b.contains("format failure rate")));
    }

    @Test
    void aFileWithoutABarFailsLoudly() throws Exception {
        Thresholds t = Thresholds.parse("{\"deterministic\":{}}");
        assertThrows(IllegalStateException.class, t::minDeterministicPassRate);
    }
}
