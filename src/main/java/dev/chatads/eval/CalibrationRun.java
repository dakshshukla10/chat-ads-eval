package dev.chatads.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.chatads.eval.judge.Judge;
import dev.chatads.eval.judge.JudgeDimension;
import dev.chatads.eval.judge.JudgeInput;
import dev.chatads.eval.judge.JudgeResult;
import dev.chatads.eval.judge.Rubric;
import dev.chatads.llm.TokenUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Checks the judge itself against the hand-labeled set in {@code evals/judge/calibration.jsonl}.
 * A "good" case agrees when it scores 4 or 5, a "bad" case when it scores 1 or 2; 3 is a miss.
 * Borderline cases are scored and listed but left out of the agreement rate. Format failures count as misses.
 */
final class CalibrationRun {

    private CalibrationRun() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CalibrationCase(String id, String dimension, String prompt, String answer, JudgeInput.Ad ad,
                           String expected, boolean borderline) {
    }

    record Counts(int checked, int agreed) {
        double rate() {
            return checked == 0 ? 0 : (double) agreed / checked;
        }
    }

    record Outcome(Counts overall, Map<JudgeDimension, Counts> perDimension, List<String> missed, TokenUsage usage, ArrayNode items) { }

    static List<CalibrationCase> load(Path file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<CalibrationCase> out = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (!line.isBlank()) {
                out.add(mapper.readValue(line, CalibrationCase.class));
            }
        }
        return out;
    }

    static Outcome run(Judge judge, Map<JudgeDimension, Rubric> rubrics, List<CalibrationCase> cases) {
        Map<JudgeDimension, Counts> per = new EnumMap<>(JudgeDimension.class);
        List<String> missed = new ArrayList<>();
        ArrayNode items = new ObjectMapper().createArrayNode();
        int checked = 0;
        int agreed = 0;
        TokenUsage usage = TokenUsage.NONE;

        for (CalibrationCase c : cases) {
            JudgeDimension d = JudgeDimension.fromKey(c.dimension());
            JudgeResult r = judge.score(rubrics.get(d), new JudgeInput(c.prompt(), c.answer(), c.ad()));
            usage = usage.plus(r.usage());
            boolean good = c.expected().equals("good");
            boolean agrees = !r.formatFailure() && (good ? r.score() >= 4 : r.score() <= 2);
            String shown = r.formatFailure() ? "format failure" : String.valueOf(r.score());
            System.out.printf("%-12s %-22s expected %-4s got %-14s %s%s%n", c.id(), d.key(), c.expected(), shown,
                    agrees ? "agree" : "MISS", c.borderline() ? " (borderline, not counted)" : "");
            items.addObject().put("case", c.id()).put("dimension", d.key()).put("expected", c.expected())
                    .put("score", r.formatFailure() ? null : r.score()).put("agreed", agrees).put("borderline", c.borderline());
            if (c.borderline()) {
                continue;
            }
            checked++;
            Counts old = per.getOrDefault(d, new Counts(0, 0));
            per.put(d, new Counts(old.checked() + 1, old.agreed() + (agrees ? 1 : 0)));
            if (agrees) {
                agreed++;
            } else {
                missed.add(c.id());
            }
        }
        return new Outcome(new Counts(checked, agreed), per, missed, usage, items);
    }
}
