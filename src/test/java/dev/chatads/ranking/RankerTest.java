package dev.chatads.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.chatads.retrieval.Product;
import dev.chatads.retrieval.ScoredProduct;
import java.util.List;
import org.junit.jupiter.api.Test;

class RankerTest {

    private static ScoredProduct sp(String id, int score) {
        return new ScoredProduct(new Product(id, id, "c", List.of()), score, List.of());
    }

    @Test
    void ordersByScoreThenIdAndCaps() {
        List<ScoredProduct> out = new Ranker().rank(List.of(sp("b", 3), sp("a", 3), sp("c", 9), sp("d", 1)), 3);
        assertEquals(List.of("c", "a", "b"), out.stream().map(s -> s.product().id()).toList());
    }
}
