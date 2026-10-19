package dev.chatads.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordRetrieverTest {

    private final KeywordRetriever retriever = new KeywordRetriever(Catalog.loadDefault());

    @Test
    void titleMatchRanksFirst() {
        List<ScoredProduct> hits = retriever.retrieve("I need new running shoes", 5);
        assertEquals("p-1001", hits.get(0).product().id());
    }

    @Test
    void unrelatedPromptReturnsNothing() {
        assertTrue(retriever.retrieve("What is the capital of France?", 5).isEmpty());
    }

    @Test
    void limitIsRespected() {
        assertTrue(retriever.retrieve("camping hiking tent water filter headlamp", 2).size() <= 2);
    }

    @Test
    void catalogHasUniqueIdsAndCategories() {
        Catalog catalog = Catalog.loadDefault();
        assertTrue(catalog.products().size() > 30);
        assertEquals("footwear", catalog.categoryOf("p-1001").orElseThrow());
    }
}
