package dev.chatads.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"chatads.db.url=", "chatads.rate-limit.per-minute=3"})
@AutoConfigureMockMvc
class RateLimitHttpTest {

    @Autowired MockMvc mvc;

    @Test
    void aClientOverTheLimitGetsA429ButTheLandingPageStaysOpen() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/v1/ads/contextual").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"prompt\":\"running shoes\"}")).andExpect(status().isOk());
        }
        mvc.perform(post("/v1/ads/contextual").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"running shoes\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }
}
