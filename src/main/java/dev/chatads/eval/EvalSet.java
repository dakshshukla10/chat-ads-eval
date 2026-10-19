package dev.chatads.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Loads a JSONL eval set: one JSON object per line, blank lines and {@code #} lines ignored. */
public final class EvalSet {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EvalSet() {
    }

    public static List<EvalCase> load(Path file) throws IOException {
        List<EvalCase> cases = new ArrayList<>();
        int lineNo = 0;
        for (String line : Files.readAllLines(file)) {
            lineNo++;
            String s = line.strip();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            EvalCase c = MAPPER.readValue(s, EvalCase.class);
            if (c.id() == null || c.prompt() == null || c.expectedCategories() == null) {
                throw new IOException(file + ":" + lineNo + " needs id, prompt and expected_categories");
            }
            cases.add(c);
        }
        return cases;
    }
}
