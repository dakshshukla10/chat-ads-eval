package dev.chatads.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The eval set on the in-memory keyword retriever (no database), which is also the fallback path.
 * Cases tagged {@code hybrid_only} are the ones keyword matching cannot answer, and they must fail here;
 * if one starts passing, the tag is stale.
 */
@SpringBootTest(properties = "chatads.db.url=")
@AutoConfigureMockMvc
class EvalSetTest {

    @Autowired
    MockMvc mvc;

    @Test
    void everyCaseThatKeywordMatchingCanAnswerPasses() throws Exception {
        List<EvalCase> cases = EvalSetRunner.load();
        assertTrue(cases.size() >= 30, "eval set should have at least 30 prompts");
        Map<String, String> failed = EvalSetRunner.failures(mvc, cases);

        List<String> unexpected = cases.stream()
                .filter(c -> !c.tags().contains("hybrid_only"))
                .map(EvalCase::id)
                .filter(failed::containsKey)
                .toList();
        assertEquals(List.of(), unexpected, () -> "keyword retriever failed: " + failed);
    }

    @Test
    void hybridOnlyCasesReallyFailOnKeywordMatching() throws Exception {
        List<EvalCase> cases = EvalSetRunner.load();
        Map<String, String> failed = EvalSetRunner.failures(mvc, cases);
        List<String> staleTags = cases.stream()
                .filter(c -> c.tags().contains("hybrid_only"))
                .map(EvalCase::id)
                .filter(id -> !failed.containsKey(id))
                .toList();
        assertEquals(List.of(), staleTags, "these cases are tagged hybrid_only but the keyword retriever passes them");
    }
}
