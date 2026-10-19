package dev.chatads.eval.judge;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;

/**
 * Strict reader for the judge's reply: one JSON object with exactly {@code score} (an integer from 1
 * to 5) and {@code reason} (a non-blank string). Markdown fences, trailing text, extra keys, decimals
 * and out-of-range scores are all rejected. A rejected reply is a format failure, never a guessed score.
 */
public final class VerdictParser {

    public record Verdict(int score, String reason) { }

    public static final class FormatException extends Exception {
        public FormatException(String message) {
            super(message);
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final int MAX_REASON_CHARS = 300;

    private VerdictParser() {
    }

    public static Verdict parse(String content) throws FormatException {
        JsonNode root;
        try {
            root = MAPPER.readTree(content == null ? "" : content.strip());
        } catch (Exception e) {
            throw new FormatException("reply is not a single JSON value");
        }
        if (root == null || !root.isObject()) {
            throw new FormatException("reply is not a JSON object");
        }
        Iterator<String> names = root.fieldNames();
        int count = 0;
        while (names.hasNext()) {
            String n = names.next();
            count++;
            if (!n.equals("score") && !n.equals("reason")) {
                throw new FormatException("unexpected key " + n);
            }
        }
        if (count != 2) {
            throw new FormatException("reply needs exactly score and reason");
        }
        JsonNode score = root.get("score");
        if (!score.isIntegralNumber() || !score.canConvertToInt() || score.asInt() < 1 || score.asInt() > 5) {
            throw new FormatException("score must be an integer from 1 to 5");
        }
        JsonNode reason = root.get("reason");
        if (!reason.isTextual() || reason.asText().isBlank()) {
            throw new FormatException("reason must be a non-blank string");
        }
        String text = reason.asText().strip();
        return new Verdict(score.asInt(), text.length() > MAX_REASON_CHARS ? text.substring(0, MAX_REASON_CHARS) : text);
    }
}
