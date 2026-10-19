package dev.chatads.eval.judge;

/** The three things the judge scores, each with its own rubric file under {@code evals/judge/}. */
public enum JudgeDimension {
    RELEVANCE("relevance", "relevance.md"),
    AD_ANSWER_SEPARATION("ad_answer_separation", "ad-answer-separation.md"),
    SAFETY("safety", "safety.md");

    private final String key;
    private final String fileName;

    JudgeDimension(String key, String fileName) {
        this.key = key;
        this.fileName = fileName;
    }

    public String key() {
        return key;
    }

    public String fileName() {
        return fileName;
    }

    public static JudgeDimension fromKey(String key) {
        for (JudgeDimension d : values()) {
            if (d.key.equals(key)) {
                return d;
            }
        }
        throw new IllegalArgumentException("unknown judge dimension " + key);
    }
}
