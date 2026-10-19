package dev.chatads.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chatads.policy.PolicyFilter;
import dev.chatads.retrieval.Catalog;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Runs the whole eval set in-process, so a bad dataset edit fails the normal build too. */
@SpringBootTest
@AutoConfigureMockMvc
class EvalSetTest {

    @Autowired
    MockMvc mvc;

    @Test
    void everyCasePassesEveryCheck() throws Exception {
        List<EvalCase> cases = EvalSet.load(Path.of("evals/prompts.jsonl"));
        assertTrue(cases.size() >= 30, "eval set should have about 30 prompts");
        CaseEvaluator evaluator = new CaseEvaluator(Catalog.loadDefault(), PolicyFilter.loadBannedCategories());
        ObjectMapper mapper = new ObjectMapper();

        for (EvalCase c : cases) {
            String body = mvc.perform(post("/v1/ads/contextual")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(mapper.createObjectNode().put("prompt", c.prompt()))))
                    .andReturn().getResponse().getContentAsString();
            String failures = evaluator.evaluate(c, body).stream()
                    .filter(r -> !r.passed())
                    .map(r -> r.name() + ": " + r.detail())
                    .collect(Collectors.joining("; "));
            assertEquals("", failures, c.id() + " (" + c.prompt() + ")");
        }
    }
}
