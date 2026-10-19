package dev.chatads.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chatads.embedding.Embedder;
import dev.chatads.embedding.EmbeddingResult;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridRetrieverTest {

    private static final Product SHOES = new Product("p1", "Trail Running Shoes", "footwear", List.of("running", "jogging"));
    private static final Product BOOTS = new Product("p2", "Alpine Hiking Boots", "footwear", List.of("hiking", "trail"));
    private static final Product MAT = new Product("p3", "Yoga Mat", "fitness", List.of("yoga", "stretching"));

    /** Returns canned hits so the fusion and the gates can be tested without a database. */
    private record FakeIndex(List<ProductIndex.Hit> keyword, List<ProductIndex.Hit> vector) implements ProductIndex {
        @Override
        public List<Hit> keywordSearch(Collection<String> terms, int limit) {
            return keyword;
        }

        @Override
        public List<Hit> vectorSearch(float[] query, int limit) {
            return vector;
        }
    }

    private static final Embedder ONES = new Embedder() {
        @Override
        public EmbeddingResult embed(String text) {
            return new EmbeddingResult(new float[] {1f, 0f}, 0, false);
        }

        @Override
        public int dimensions() {
            return 2;
        }

        @Override
        public String id() {
            return "test";
        }
    };

    private static HybridRetriever retriever(List<ProductIndex.Hit> keyword, List<ProductIndex.Hit> vector) {
        return new HybridRetriever(new FakeIndex(keyword, vector), ONES, 10, 60, 0.3, 0.5);
    }

    private static ProductIndex.Hit hit(Product p, double score) {
        return new ProductIndex.Hit(p, score);
    }

    @Test
    void aProductFoundByBothSidesOutranksOneFoundByOne() {
        var r = retriever(List.of(hit(SHOES, 2.0), hit(BOOTS, 1.0)), List.of(hit(BOOTS, 0.8), hit(SHOES, 0.7)));
        List<ScoredProduct> out = r.retrieve("trail running shoes hiking", 5);
        assertEquals(List.of("p1", "p2"), out.stream().map(s -> s.product().id()).toList());
        assertTrue(out.get(0).score() > 1.0 / 61);
    }

    @Test
    void aVectorOnlyHitSurvivesWhenThePromptSharesNoWords() {
        var r = retriever(List.of(), List.of(hit(SHOES, 0.55)));
        List<ScoredProduct> out = r.retrieve("runing shose", 5);
        assertEquals("p1", out.get(0).product().id());
        assertTrue(out.get(0).matchedTerms().isEmpty());
    }

    @Test
    void aSingleWeakKeywordMatchIsGatedOut() {
        // "jogging" is a keyword of SHOES but not in the title: one point, below the two needed.
        var r = retriever(List.of(hit(SHOES, 0.4)), List.of());
        assertTrue(r.retrieve("jogging", 5).isEmpty());
    }

    @Test
    void aTitleMatchPassesTheKeywordGateAndReportsTheMatchedTerms() {
        var r = retriever(List.of(hit(SHOES, 1.6)), List.of());
        List<ScoredProduct> out = r.retrieve("running shoes", 5);
        assertTrue(out.get(0).matchedTerms().containsAll(List.of("running", "shoe")));
    }

    @Test
    void vectorHitsBelowTheFloorOrFarBehindTheBestAreDropped() {
        var r = retriever(List.of(), List.of(hit(SHOES, 0.8), hit(BOOTS, 0.35), hit(MAT, 0.2)));
        // 0.35 is above min-similarity (0.3) but below half of 0.8; 0.2 is below min-similarity.
        List<ScoredProduct> out = r.retrieve("shoes", 5);
        assertEquals(List.of("p1"), out.stream().map(s -> s.product().id()).toList());
    }

    @Test
    void limitIsApplied() {
        var r = retriever(List.of(), List.of(hit(SHOES, 0.9), hit(BOOTS, 0.8), hit(MAT, 0.7)));
        assertEquals(2, r.retrieve("anything", 2).size());
    }

    @Test
    void aPromptWithNoSearchableWordsSkipsBothSides() {
        Embedder zero = new Embedder() {
            @Override
            public EmbeddingResult embed(String text) {
                return new EmbeddingResult(new float[] {0f, 0f}, 0, false);
            }

            @Override
            public int dimensions() {
                return 2;
            }

            @Override
            public String id() {
                return "zero";
            }
        };
        var r = new HybridRetriever(new FakeIndex(List.of(hit(SHOES, 5)), List.of(hit(SHOES, 0.9))), zero, 10, 60, 0.3, 0.5);
        assertTrue(r.retrieve("???", 5).isEmpty());
    }
}
