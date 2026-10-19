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

@SpringBootTest(properties = {"chatads.cache.enabled=true", "chatads.db.url=",
        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1"})
@AutoConfigureMockMvc
class RedisCacheFailureTest {

    @Autowired MockMvc mvc;
    @Autowired MeterRegistry registry;

    @Test
    void deadRedisServesAdsAndCountsTheCacheError() throws Exception {
        double errors = registry.get("chatads.cache").tag("result", "error").counter().count();
        double fallbacks = registry.get("chatads.fallback").tag("reason", "cache_error").counter().count();
        mvc.perform(post("/v1/ads/contextual").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"running shoes for a marathon\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsored[0].product_id").value("p-1001"));
        assertEquals(errors + 1, registry.get("chatads.cache").tag("result", "error").counter().count());
        assertEquals(fallbacks + 1, registry.get("chatads.fallback").tag("reason", "cache_error").counter().count());
    }
}
