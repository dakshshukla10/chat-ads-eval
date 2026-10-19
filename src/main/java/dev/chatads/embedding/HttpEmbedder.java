package dev.chatads.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls a real embedding model over HTTP. {@link Flavor#OPENAI} speaks the OpenAI-compatible
 * {@code POST {base}/embeddings}; {@link Flavor#OLLAMA} speaks Ollama's {@code POST {base}/api/embed}.
 * Both report the input token count, which is passed on; if a server leaves it out the
 * count is estimated and flagged as such.
 */
public class HttpEmbedder implements Embedder {

    public enum Flavor { OPENAI, OLLAMA }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Flavor flavor;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final int dimensions;
    private final Duration timeout;
    private final HttpClient http;

    public HttpEmbedder(Flavor flavor, String baseUrl, String model, String apiKey, int dimensions, Duration timeout) {
        if (baseUrl == null || baseUrl.isBlank() || model == null || model.isBlank()) {
            throw new IllegalArgumentException("an embedding provider needs a base url and a model");
        }
        this.flavor = flavor;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.dimensions = dimensions;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public EmbeddingResult embed(String text) {
        try {
            ObjectNode body = MAPPER.createObjectNode().put("model", model).put("input", text);
            String path = flavor == Flavor.OPENAI ? "/embeddings" : "/api/embed";
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            if (apiKey != null && !apiKey.isBlank()) {
                req.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("embedding call failed with HTTP " + resp.statusCode());
            }
            return parse(MAPPER.readTree(resp.body()), text);
        } catch (IOException e) {
            throw new IllegalStateException("embedding call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding call interrupted", e);
        }
    }

    EmbeddingResult parse(JsonNode root, String text) {
        JsonNode vectorNode;
        JsonNode usage;
        if (flavor == Flavor.OPENAI) {
            vectorNode = root.path("data").path(0).path("embedding");
            usage = root.path("usage").path("prompt_tokens");
        } else {
            vectorNode = root.path("embeddings").path(0);
            usage = root.path("prompt_eval_count");
        }
        if (!vectorNode.isArray() || vectorNode.isEmpty()) {
            throw new IllegalStateException("embedding response has no vector");
        }
        if (vectorNode.size() != dimensions) {
            throw new IllegalStateException("model returned " + vectorNode.size() + " dimensions but "
                    + "chatads.embedding.dimensions is " + dimensions);
        }
        float[] v = new float[dimensions];
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) vectorNode.get(i).asDouble();
        }
        boolean reported = usage.isInt() || usage.isLong();
        int tokens = reported ? usage.asInt() : TokenEstimator.estimate(text);
        return new EmbeddingResult(v, tokens, !reported);
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String id() {
        return flavor.name().toLowerCase() + ":" + model + ":" + dimensions;
    }

    public String model() {
        return model;
    }
}
