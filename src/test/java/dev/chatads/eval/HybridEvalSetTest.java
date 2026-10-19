package dev.chatads.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.chatads.support.Containers;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The whole eval set on hybrid retrieval against a real Postgres with pgvector. */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIf(value = "dev.chatads.support.Containers#dockerAvailable", disabledReason = "Docker is not available")
class HybridEvalSetTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        var pg = Containers.postgres();
        registry.add("chatads.db.url", pg::getJdbcUrl);
        registry.add("chatads.db.user", pg::getUsername);
        registry.add("chatads.db.password", pg::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void everyCasePassesEveryCheck() throws Exception {
        Map<String, String> failed = EvalSetRunner.failures(mvc, EvalSetRunner.load());
        assertEquals(Map.of(), failed);
    }
}
