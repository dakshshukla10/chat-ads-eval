package dev.chatads.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PriceTableTest {

    private final PriceTable prices = PriceTable.load();

    @Test
    void pricedModelsCostTheirPerMillionRate() {
        double cost = prices.cost("gpt-4o-mini", new TokenUsage(2_000_000, 1_000_000, false));
        assertEquals(2 * 0.15 + 0.60, cost, 1e-9);
        assertTrue(prices.isPriced("text-embedding-3-small"));
    }

    @Test
    void unlistedModelsAreFreeAndReportedAsUnpriced() {
        assertEquals(0.0, prices.cost("qwen2.5:0.5b-instruct", new TokenUsage(1000, 1000, false)));
        assertFalse(prices.isPriced("qwen2.5:0.5b-instruct"));
    }
}
