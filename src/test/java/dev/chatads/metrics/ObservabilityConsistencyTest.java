package dev.chatads.metrics;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.chatads.llm.TokenUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every metric the Grafana dashboard and the Prometheus alert rules mention must be one the service
 * really exports, so a rename in Java cannot leave a panel or an alert quietly empty.
 */
@SpringBootTest(properties = "chatads.db.url=")
@AutoConfigureMockMvc
@AutoConfigureObservability
class ObservabilityConsistencyTest {

    private static final Pattern METRIC = Pattern.compile("\\b(chatads_[a-z_]+|http_server_requests_seconds_[a-z]+)\\b");

    @Autowired
    MockMvc mvc;

    @Autowired
    LlmUsageMetrics llmUsage;

    @Test
    void dashboardAndAlertsOnlyUseExportedMetrics() throws Exception {
        mvc.perform(post("/v1/ads/contextual").contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"running shoes\"}")).andReturn();
        // Token series appear on first use; a real embedding provider would create them.
        llmUsage.record("embedding", "test-model", new TokenUsage(5, 0, false));

        String scrape = mvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getContentAsString();
        TreeSet<String> exported = new TreeSet<>();
        for (String line : scrape.split("\n")) {
            if (!line.startsWith("#")) {
                exported.add(line.split("[{ ]")[0]);
            }
        }

        List<String> sources = new ArrayList<>();
        sources.add(Files.readString(Path.of("observability/grafana/dashboards/overview.json")));
        sources.add(Files.readString(Path.of("observability/alerts.yml")));

        TreeSet<String> missing = new TreeSet<>();
        int used = 0;
        for (String text : sources) {
            Matcher m = METRIC.matcher(text);
            while (m.find()) {
                used++;
                if (!exported.contains(m.group(1))) {
                    missing.add(m.group(1));
                }
            }
        }
        assertTrue(used > 10, "expected the dashboard and rules to reference metrics");
        assertTrue(missing.isEmpty(), "referenced but not exported: " + missing);
    }
}
