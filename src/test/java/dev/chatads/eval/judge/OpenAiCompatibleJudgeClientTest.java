package dev.chatads.eval.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleJudgeClientTest {

    private HttpServer server;
    private final AtomicReference<JsonNode> lastRequest = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();

    private String serve(int firstStatus, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            int n = calls.incrementAndGet();
            lastRequest.set(new ObjectMapper().readTree(exchange.getRequestBody().readAllBytes()));
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            int status = n == 1 ? firstStatus : 200;
            byte[] out = (status == 200 ? body : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static final String OK = "{\"choices\":[{\"message\":{\"content\":\"{\\\"score\\\":4,\\\"reason\\\":\\\"ok\\\"}\"}}],"
            + "\"usage\":{\"prompt_tokens\":321,\"completion_tokens\":12}}";

    @Test
    void sendsAJsonModeRequestAndReturnsProviderTokenCounts() throws Exception {
        String base = serve(200, OK);
        var client = new OpenAiCompatibleJudgeClient(base, "judge-model", "sk-test", Duration.ofSeconds(5));

        JudgeClient.Completion c = client.complete("system text", "user text");

        assertEquals("{\"score\":4,\"reason\":\"ok\"}", c.content());
        assertEquals(321, c.usage().inputTokens());
        assertEquals(12, c.usage().outputTokens());
        assertFalse(c.usage().estimated());
        JsonNode req = lastRequest.get();
        assertEquals("judge-model", req.path("model").asText());
        assertEquals(0, req.path("temperature").asInt());
        assertEquals("json_object", req.path("response_format").path("type").asText());
        assertEquals("system", req.path("messages").path(0).path("role").asText());
        assertEquals("user text", req.path("messages").path(1).path("content").asText());
        assertEquals("Bearer sk-test", lastAuth.get());
    }

    @Test
    void aMissingUsageBlockIsEstimatedAndFlagged() throws Exception {
        String base = serve(200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"score\\\":4,\\\"reason\\\":\\\"ok\\\"}\"}}]}");
        var client = new OpenAiCompatibleJudgeClient(base, "m", null, Duration.ofSeconds(5));

        JudgeClient.Completion c = client.complete("aaaa", "bbbb");

        assertTrue(c.usage().estimated());
        assertEquals(2, c.usage().inputTokens());
        assertEquals(null, lastAuth.get());
    }

    @Test
    void aServerErrorIsRetried() throws Exception {
        String base = serve(503, OK);
        var client = new OpenAiCompatibleJudgeClient(base, "m", null, Duration.ofSeconds(5));

        assertEquals(321, client.complete("s", "u").usage().inputTokens());
        assertEquals(2, calls.get());
    }

    @Test
    void aClientErrorFailsAtOnce() throws Exception {
        String base = serve(401, OK);
        var client = new OpenAiCompatibleJudgeClient(base, "m", null, Duration.ofSeconds(5));

        assertThrows(IllegalStateException.class, () -> client.complete("s", "u"));
        assertEquals(1, calls.get());
    }

    @Test
    void theJudgeIsSkippedUnlessAKeyOrBaseUrlIsSet() {
        assertTrue(JudgeConfig.fromEnv(Map.of()).isEmpty());
        assertTrue(JudgeConfig.fromEnv(Map.of("LLM_JUDGE_API_KEY", "  ")).isEmpty());
        assertThrows(IllegalStateException.class, () -> JudgeConfig.fromEnv(Map.of("LLM_JUDGE_BASE_URL", "http://localhost:11434/v1")));
        assertEquals("qwen", JudgeConfig.fromEnv(Map.of("LLM_JUDGE_BASE_URL", "http://localhost:11434/v1", "LLM_JUDGE_MODEL", "qwen"))
                .orElseThrow().model());
    }
}
