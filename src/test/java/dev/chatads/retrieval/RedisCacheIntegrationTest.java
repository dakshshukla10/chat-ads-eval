package dev.chatads.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chatads.embedding.Embedder;
import dev.chatads.support.Containers;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"chatads.cache.enabled=true", "chatads.cache.ttl=PT2S", "chatads.db.url="})
@AutoConfigureMockMvc
@AutoConfigureObservability
@EnabledIf(value = "dev.chatads.support.Containers#dockerAvailable", disabledReason = "Docker is not available")
class RedisCacheIntegrationTest {

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        var redis = Containers.redis();
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;
    @Autowired MeterRegistry registry;
    @Autowired Embedder embedder;
    @Autowired ObjectMapper json;

    private final List<String> keys = new ArrayList<>();
    private double hits;
    private double misses;

    @BeforeEach
    void baseline() {
        hits = count("hit");
        misses = count("miss");
    }

    @AfterEach
    void removeEntries() {
        redis.delete(keys);
    }

    private double count(String result) {
        return registry.get("chatads.cache").tag("result", result).counter().count();
    }

    private String key(String prompt) {
        String key = CachingRetriever.key(embedder.id(), prompt, 10);
        keys.add(key);
        redis.delete(key);
        return key;
    }

    private JsonNode request(String prompt) throws Exception {
        String response = mvc.perform(post("/v1/ads/contextual").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("prompt", prompt))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    @Test
    void normalizedRepeatHitsTheCacheAndExposesCounters() throws Exception {
        key("running shoes");
        JsonNode first = request("Running  Shoes!");
        JsonNode second = request("running shoes");
        assertTrue(!first.path("sponsored").isEmpty());
        assertEquals(first.path("sponsored"), second.path("sponsored"));
        assertEquals(hits + 1, count("hit"));
        assertEquals(misses + 1, count("miss"));
        String scrape = mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (String result : List.of("hit", "miss", "error")) {
            assertTrue(scrape.contains("chatads_cache_total{result=\"" + result + "\"}"));
        }
    }

    @Test
    void cachedBannedCandidateStillPassesThroughPolicyFiltering() throws Exception {
        String key = key("cached banned candidate");
        redis.opsForValue().set(key, "[{\"id\":\"p-9001\",\"score\":10,\"matched\":[\"vape\"]}]",
                Duration.ofSeconds(2));
        double filtered = registry.get("chatads.fallback").tag("reason", "policy_filtered").counter().count();
        mvc.perform(post("/v1/ads/contextual").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"cached banned candidate\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sponsored").isEmpty());
        assertEquals(hits + 1, count("hit"));
        assertEquals(misses, count("miss"));
        assertEquals(filtered + 1, registry.get("chatads.fallback")
                .tag("reason", "policy_filtered").counter().count());
    }

    @Test
    void entriesHaveTheConfiguredTtlAndAreFetchedAgainAfterExpiry() throws Exception {
        String key = key("marathon running shoes");
        JsonNode first = request("marathon running shoes");
        var container = Containers.redis();
        long ttl = Long.parseLong(container.execInContainer("redis-cli", "PTTL", key).getStdout().trim());
        assertTrue(ttl > 0 && ttl <= 2_000, "Redis must receive the configured two-second TTL");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (Boolean.TRUE.equals(redis.hasKey(key)) && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertEquals(Boolean.FALSE, redis.hasKey(key));
        assertEquals(first.path("sponsored"), request("marathon running shoes").path("sponsored"));
        assertEquals(misses + 2, count("miss"));
        assertEquals(hits, count("hit"));
        assertTrue(redis.getExpire(key, TimeUnit.MILLISECONDS) > 0);
    }
}
