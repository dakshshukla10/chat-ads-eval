package dev.chatads.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpEmbedderTest {

    private HttpServer server;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    private String serve(String responseBody) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().getPath());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void openAiCompatibleResponseGivesTheProviderTokenCount() throws Exception {
        String base = serve("{\"data\":[{\"embedding\":[0.1,0.2,0.3]}],\"usage\":{\"prompt_tokens\":7,\"total_tokens\":7}}");
        var e = new HttpEmbedder(HttpEmbedder.Flavor.OPENAI, base + "/v1", "small-embed", "k-123", 3, Duration.ofSeconds(2));
        EmbeddingResult r = e.embed("running shoes");
        assertEquals(3, r.vector().length);
        assertEquals(7, r.inputTokens());
        assertFalse(r.tokensEstimated());
        assertEquals("/v1/embeddings", lastPath.get());
        assertEquals("Bearer k-123", lastAuth.get());
    }

    @Test
    void ollamaResponseUsesPromptEvalCount() throws Exception {
        String base = serve("{\"embeddings\":[[0.5,0.5]],\"prompt_eval_count\":4}");
        var e = new HttpEmbedder(HttpEmbedder.Flavor.OLLAMA, base, "all-minilm", "", 2, Duration.ofSeconds(2));
        EmbeddingResult r = e.embed("blender");
        assertEquals(4, r.inputTokens());
        assertEquals("/api/embed", lastPath.get());
    }

    @Test
    void aMissingUsageBlockFallsBackToTheEstimate() throws Exception {
        String base = serve("{\"data\":[{\"embedding\":[1,0]}]}");
        var e = new HttpEmbedder(HttpEmbedder.Flavor.OPENAI, base, "m", null, 2, Duration.ofSeconds(2));
        EmbeddingResult r = e.embed("twelve chars");
        assertTrue(r.tokensEstimated());
        assertEquals(TokenEstimator.estimate("twelve chars"), r.inputTokens());
        assertEquals(3, r.inputTokens());
    }

    @Test
    void aWrongVectorSizeIsAnError() throws Exception {
        String base = serve("{\"data\":[{\"embedding\":[1,0,0]}]}");
        var e = new HttpEmbedder(HttpEmbedder.Flavor.OPENAI, base, "m", null, 2, Duration.ofSeconds(2));
        assertThrows(IllegalStateException.class, () -> e.embed("x"));
    }
}
