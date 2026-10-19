package dev.chatads.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Dollar prices per million tokens, read from {@code llm-prices.json} (the classpath copy, or the
 * file named by the {@code CHATADS_PRICES_FILE} environment variable). A model that is not in the
 * table costs zero and is reported as unpriced, so a local model never invents a bill.
 */
public final class PriceTable {

    public record Price(double inputPerMillion, double outputPerMillion) { }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String PRICES_FILE_ENV = "CHATADS_PRICES_FILE";

    private final Map<String, Price> prices;

    public PriceTable(Map<String, Price> prices) {
        this.prices = Map.copyOf(prices);
    }

    public static PriceTable load() {
        String override = System.getenv(PRICES_FILE_ENV);
        try {
            if (override != null && !override.isBlank()) {
                return parse(MAPPER.readTree(Files.readAllBytes(Path.of(override))));
            }
            try (InputStream in = PriceTable.class.getResourceAsStream("/llm-prices.json")) {
                if (in == null) {
                    throw new IllegalStateException("llm-prices.json not found on classpath");
                }
                return parse(MAPPER.readTree(in));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the price table", e);
        }
    }

    static PriceTable parse(JsonNode root) {
        Map<String, Price> out = new HashMap<>();
        root.path("models").fields().forEachRemaining(e -> out.put(e.getKey(),
                new Price(e.getValue().path("input_per_million").asDouble(0), e.getValue().path("output_per_million").asDouble(0))));
        return new PriceTable(out);
    }

    public boolean isPriced(String model) {
        return prices.containsKey(model);
    }

    /** Estimated dollars for the call; zero for models not in the table. */
    public double cost(String model, TokenUsage usage) {
        Price p = prices.get(model);
        if (p == null) {
            return 0;
        }
        return usage.inputTokens() * p.inputPerMillion() / 1_000_000.0
                + usage.outputTokens() * p.outputPerMillion() / 1_000_000.0;
    }
}
