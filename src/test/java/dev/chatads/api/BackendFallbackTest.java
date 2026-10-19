package dev.chatads.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Postgres is configured but nothing listens on the port: ads must still come back, from the keyword retriever. */
@SpringBootTest(properties = {
        "chatads.db.url=jdbc:postgresql://127.0.0.1:1/chatads",
        "chatads.db.password=none"})
@AutoConfigureMockMvc
class BackendFallbackTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    MeterRegistry registry;

    @Test
    void deadDatabaseServesFromTheKeywordRetrieverAndCountsTheFallback() throws Exception {
        mvc.perform(post("/v1/ads/contextual").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"running shoes for a marathon\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsored[0].product_id").value("p-1001"));

        double count = registry.get("chatads.fallback").tag("reason", "backend_error").counter().count();
        assertEquals(1.0, count);
    }
}
