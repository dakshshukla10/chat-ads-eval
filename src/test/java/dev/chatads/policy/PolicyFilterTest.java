package dev.chatads.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chatads.retrieval.Product;
import dev.chatads.retrieval.ScoredProduct;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyFilterTest {

    private static ScoredProduct sp(String id, String category) {
        return new ScoredProduct(new Product(id, "T " + id, category, List.of()), 5, List.of("x"));
    }

    @Test
    void defaultsLoadTheBannedList() {
        assertTrue(PolicyFilter.loadBannedCategories().containsAll(List.of("tobacco", "weapons", "alcohol", "gambling")));
    }

    @Test
    void bannedCategoriesAreRemoved() {
        PolicyFilter filter = PolicyFilter.withDefaults();
        List<ScoredProduct> out = filter.apply(List.of(sp("a", "footwear"), sp("b", "Tobacco"), sp("c", "weapons")));
        assertEquals(1, out.size());
        assertEquals("a", out.get(0).product().id());
        assertFalse(filter.isAllowed(sp("d", "gambling")));
    }
}
