package dev.chatads.api;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.chatads.embedding.Embedder;
import dev.chatads.embedding.HashingEmbedder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The hosted configuration: demo profile, with a provider configured that the demo must ignore. */
@SpringBootTest(properties = {"chatads.db.url=", "chatads.embedding.provider=openai",
        "chatads.embedding.base-url=http://127.0.0.1:1", "chatads.embedding.model=ignored",
        "chatads.rate-limit.per-minute=1000"})
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoModeTest {

    @Autowired MockMvc mvc;
    @Autowired Embedder embedder;

    private org.springframework.test.web.servlet.ResultActions call(String prompt) throws Exception {
        return mvc.perform(post("/v1/ads/contextual").contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"" + prompt + "\"}"));
    }

    @Test
    void embeddingsStayOfflineWhateverIsConfigured() {
        assertInstanceOf(HashingEmbedder.class, embedder);
    }

    @Test
    void landingPageIsServedAtTheRoot() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Shopper prompt")));
        mvc.perform(get("/demo.js")).andExpect(status().isOk());
    }

    @Test
    void metricsAreNotPublicButHealthIs() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void everySamplePromptOnTheLandingPageBehavesAsDescribed() throws Exception {
        call("noise cancelling headphones for my commute").andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsored[0].product_id").value("p-1201"))
                .andExpect(jsonPath("$.sponsored[0].label").value("Sponsored"));
        call("lightweight laptop for a student").andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsored[0].product_id").value("p-1101"));
        call("where can I buy a vape").andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsored").isEmpty());
    }
}
