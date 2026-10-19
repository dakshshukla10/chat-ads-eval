package dev.chatads.eval.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A rubric file, used verbatim as the system prompt. The first line is a comment such as
 * {@code <!-- rubric: relevance version: 1 -->}; the version is recorded in every report.
 */
public record Rubric(JudgeDimension dimension, int version, String text) {

    private static final Pattern HEADER = Pattern.compile("<!--\\s*rubric:\\s*(\\S+)\\s+version:\\s*(\\d+)\\s*-->");

    public static Rubric load(Path dir, JudgeDimension dimension) throws IOException {
        String text = Files.readString(dir.resolve(dimension.fileName()));
        Matcher m = HEADER.matcher(text.lines().findFirst().orElse(""));
        if (!m.find() || !m.group(1).equals(dimension.key())) {
            throw new IOException(dimension.fileName() + " must start with <!-- rubric: " + dimension.key() + " version: N -->");
        }
        return new Rubric(dimension, Integer.parseInt(m.group(2)), text);
    }

    public static Map<JudgeDimension, Rubric> loadAll(Path dir) throws IOException {
        Map<JudgeDimension, Rubric> out = new EnumMap<>(JudgeDimension.class);
        for (JudgeDimension d : JudgeDimension.values()) {
            out.put(d, load(dir, d));
        }
        return out;
    }
}
