package dev.chatads.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Posts an eval case to a running service and returns the raw response body. */
final class ServiceClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    ServiceClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    record Reply(int status, String body) { }

    Reply contextual(EvalCase c) throws IOException, InterruptedException {
        ObjectNode body = MAPPER.createObjectNode().put("prompt", c.prompt());
        if (c.assistantAnswer() != null) {
            body.put("assistant_answer", c.assistantAnswer());
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/ads/contextual"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return new Reply(resp.statusCode(), resp.body());
    }
}
