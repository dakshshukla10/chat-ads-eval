package dev.chatads.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.chatads.eval.judge.DimensionStats;
import dev.chatads.eval.judge.Judge;
import dev.chatads.eval.judge.JudgeDimension;
import dev.chatads.eval.judge.JudgeInput;
import dev.chatads.eval.judge.JudgeResult;
import dev.chatads.eval.judge.Rubric;
import dev.chatads.retrieval.Catalog;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Asks the service for each eval case, then has the judge score the response.
 * Relevance and safety need a sponsored item; separation needs the host answer from the case.
 * A case with no ad is not sent to the judge for those two, because there is nothing to score
 * (the deterministic {@code expected_category} check already covers "no ad when none fits").
 */
final class JudgeRun {

    private JudgeRun() {
    }

    record Outcome(Map<JudgeDimension, DimensionStats> stats, ArrayNode items) { }

    static Outcome run(ServiceClient service, Judge judge, Map<JudgeDimension, Rubric> rubrics, List<EvalCase> cases)
            throws IOException, InterruptedException {
        Catalog catalog = Catalog.loadDefault();
        ObjectMapper mapper = new ObjectMapper();
        Map<JudgeDimension, DimensionStats> stats = new EnumMap<>(JudgeDimension.class);
        ArrayNode items = mapper.createArrayNode();

        for (EvalCase c : cases) {
            ServiceClient.Reply reply = service.contextual(c);
            if (reply.status() != 200) {
                throw new IOException(c.id() + ": service answered HTTP " + reply.status());
            }
            JsonNode top = mapper.readTree(reply.body()).path("sponsored").path(0);
            JudgeInput.Ad ad = null;
            if (top.isObject()) {
                String category = catalog.categoryOf(top.path("product_id").asText()).orElse("");
                ad = new JudgeInput.Ad(top.path("title").asText(), category, top.path("label").asText(), top.path("reason").asText());
            }
            JudgeInput input = new JudgeInput(c.prompt(), c.assistantAnswer(), ad);

            for (JudgeDimension d : JudgeDimension.values()) {
                boolean applies = d == JudgeDimension.AD_ANSWER_SEPARATION ? c.assistantAnswer() != null : ad != null;
                if (!applies) {
                    continue;
                }
                JudgeResult r = judge.score(rubrics.get(d), input);
                stats.merge(d, DimensionStats.EMPTY.add(r), (a, b) -> a.add(r));
                System.out.printf("%-6s %-22s %s%n", c.id(), d.key(), r.formatFailure() ? "FORMAT FAILURE (" + r.formatError() + ")" : r.score() + "  " + r.reason());
                ObjectNode item = items.addObject();
                item.put("case", c.id()).put("dimension", d.key());
                if (r.formatFailure()) {
                    item.put("format_error", r.formatError());
                } else {
                    item.put("score", r.score()).put("reason", r.reason());
                }
            }
        }
        return new Outcome(stats, items);
    }
}
