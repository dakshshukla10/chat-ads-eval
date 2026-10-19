package dev.chatads.eval.judge;

import dev.chatads.llm.TokenUsage;

/**
 * Scores one case against one rubric. A reply that is not strict JSON gets one fresh try; if the
 * second reply is also unreadable the case is recorded as a format failure instead of a score.
 */
public class Judge {

    private static final int MAX_TRIES = 2;

    private final JudgeClient client;

    public Judge(JudgeClient client) {
        this.client = client;
    }

    public JudgeResult score(Rubric rubric, JudgeInput input) {
        TokenUsage used = TokenUsage.NONE;
        String error = "no attempt";
        for (int i = 0; i < MAX_TRIES; i++) {
            JudgeClient.Completion c = client.complete(rubric.text(), input.userMessage());
            used = used.plus(c.usage());
            try {
                VerdictParser.Verdict v = VerdictParser.parse(c.content());
                return new JudgeResult(rubric.dimension(), v.score(), v.reason(), null, used);
            } catch (VerdictParser.FormatException e) {
                error = e.getMessage();
            }
        }
        return new JudgeResult(rubric.dimension(), null, null, error, used);
    }
}
