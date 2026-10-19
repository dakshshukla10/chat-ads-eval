package dev.chatads.eval.judge;

import dev.chatads.llm.TokenUsage;

/**
 * Totals for one dimension. Format failures stay in the denominator, so a model that cannot follow
 * the output format lowers the pass rate instead of being ignored.
 *
 * @param passed cases scoring 4 or 5
 */
public record DimensionStats(int total, int scored, int formatFailures, int passed, double scoreSum, TokenUsage usage) {

    public static final DimensionStats EMPTY = new DimensionStats(0, 0, 0, 0, 0, TokenUsage.NONE);

    public DimensionStats add(JudgeResult r) {
        boolean ok = !r.formatFailure();
        return new DimensionStats(total + 1, scored + (ok ? 1 : 0), formatFailures + (ok ? 0 : 1),
                passed + (ok && r.score() >= 4 ? 1 : 0), scoreSum + (ok ? r.score() : 0), usage.plus(r.usage()));
    }

    /** Mean over the cases that produced a score; 0 when none did. */
    public double meanScore() {
        return scored == 0 ? 0 : scoreSum / scored;
    }

    public double passRate() {
        return total == 0 ? 0 : (double) passed / total;
    }

    public double formatFailureRate() {
        return total == 0 ? 0 : (double) formatFailures / total;
    }
}
