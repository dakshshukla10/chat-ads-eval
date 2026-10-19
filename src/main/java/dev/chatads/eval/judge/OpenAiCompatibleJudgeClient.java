package dev.chatads.eval.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.chatads.embedding.TokenEstimator;
import dev.chatads.llm.TokenUsage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Talks to any server that implements {@code POST {base}/chat/completions}: OpenAI-style APIs, and
 * Ollama through its {@code /v1} compatibility endpoint. Temperature is 0 and a fixed seed is sent so
 * repeated runs agree as far as the server allows. JSON mode is requested; the reply is still
 * parsed strictly by {@link VerdictParser}.
 */
public class OpenAiCompatibleJudgeClient implements JudgeClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int ATTEMPTS = 3;

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient http;

    public OpenAiCompatibleJudgeClient(String baseUrl, String model, String apiKey, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public Completion complete(String systemPrompt, String userMessage) {
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                return send(systemPrompt, userMessage);
            } catch (RetryableException e) {
                last = new IllegalStateException(e.getMessage(), e);
                sleep(attempt * 1000L);
            }
        }
        throw last;
    }

    private Completion send(String systemPrompt, String userMessage) {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0);
            body.put("seed", 7);
            body.put("max_tokens", 120);
            body.putObject("response_format").put("type", "json_object");
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userMessage);

            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            if (apiKey != null && !apiKey.isBlank()) {
                req.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status == 429 || status >= 500) {
                throw new RetryableException("judge call failed with HTTP " + status);
            }
            if (status / 100 != 2) {
                throw new IllegalStateException("judge call failed with HTTP " + status + ": " + abbreviate(resp.body()));
            }
            return parse(MAPPER.readTree(resp.body()), systemPrompt + userMessage);
        } catch (IOException e) {
            throw new RetryableException("judge call failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("judge call interrupted", e);
        }
    }

    Completion parse(JsonNode root, String promptForEstimate) {
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new IllegalStateException("judge response has no message content");
        }
        JsonNode in = root.path("usage").path("prompt_tokens");
        JsonNode out = root.path("usage").path("completion_tokens");
        TokenUsage usage = in.isInt() && out.isInt()
                ? new TokenUsage(in.asInt(), out.asInt(), false)
                : new TokenUsage(TokenEstimator.estimate(promptForEstimate), TokenEstimator.estimate(content.asText()), true);
        return new Completion(content.asText(), usage);
    }

    private static String abbreviate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public String model() {
        return model;
    }

    private static final class RetryableException extends RuntimeException {
        RetryableException(String message) {
            super(message);
        }
    }
}
