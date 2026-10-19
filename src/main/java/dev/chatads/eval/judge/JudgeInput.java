package dev.chatads.eval.judge;

/**
 * What the judge sees for one case. The id and expected labels of calibration cases stay outside it.
 *
 * @param answer the host assistant's text, or null
 * @param ad the top sponsored item, or null when none was shown
 */
public record JudgeInput(String prompt, String answer, Ad ad) {

    public record Ad(String title, String category, String label, String reason) { }

    /** The user message, in the shape the rubrics describe. */
    public String userMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("SHOPPER PROMPT:\n").append(prompt).append("\n\n");
        sb.append("ASSISTANT ANSWER:\n").append(answer == null || answer.isBlank() ? "(none)" : answer).append("\n\n");
        sb.append("SPONSORED ITEM:\n");
        if (ad == null) {
            sb.append("(none)");
        } else {
            sb.append("title: ").append(nullToEmpty(ad.title())).append('\n')
                    .append("category: ").append(nullToEmpty(ad.category())).append('\n')
                    .append("label: ").append(nullToEmpty(ad.label())).append('\n')
                    .append("reason: ").append(nullToEmpty(ad.reason()));
        }
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
