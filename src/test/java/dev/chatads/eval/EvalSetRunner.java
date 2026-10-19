package dev.chatads.eval;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.chatads.policy.PolicyFilter;
import dev.chatads.retrieval.Catalog;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Pushes the eval set through the controller in-process and collects failing checks per case id. */
final class EvalSetRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EvalSetRunner() {
    }

    static List<EvalCase> load() throws Exception {
        return EvalSet.load(Path.of("evals/prompts.jsonl"));
    }

    static Map<String, String> failures(MockMvc mvc, List<EvalCase> cases) throws Exception {
        CaseEvaluator evaluator = new CaseEvaluator(Catalog.loadDefault(), PolicyFilter.loadBannedCategories());
        Map<String, String> failed = new LinkedHashMap<>();
        for (EvalCase c : cases) {
            ObjectNode body = MAPPER.createObjectNode().put("prompt", c.prompt());
            if (c.assistantAnswer() != null) {
                body.put("assistant_answer", c.assistantAnswer());
            }
            String response = mvc.perform(post("/v1/ads/contextual")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(MAPPER.writeValueAsString(body)))
                    .andReturn().getResponse().getContentAsString();
            String detail = evaluator.evaluate(c, response).stream()
                    .filter(r -> !r.passed())
                    .map(r -> r.name() + ": " + r.detail())
                    .collect(Collectors.joining("; "));
            if (!detail.isEmpty()) {
                failed.put(c.id(), detail);
            }
        }
        return failed;
    }
}
