package dev.chatads.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chatads.metrics.AdMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class CachingRetrieverTest {

    private final Catalog catalog = Catalog.loadDefault();
    private final List<ScoredProduct> candidates = List.of(
            new ScoredProduct(catalog.find("p-1001").orElseThrow(), 2.5, List.of("running", "shoe")));
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AdMetrics metrics = new AdMetrics(registry);
    private final MemoryStore store = new MemoryStore();
    private final AtomicInteger calls = new AtomicInteger();
    private final Retriever delegate = (prompt, limit) -> {
        calls.incrementAndGet();
        return candidates;
    };

    private CachingRetriever caching(Retriever source) {
        return new CachingRetriever(source, catalog, store, "hash-v1-256", Duration.ofSeconds(2), metrics);
    }

    private double count(String result) {
        return registry.get("chatads.cache").tag("result", result).counter().count();
    }

    @Test
    void keyUsesTheNormalizedPromptEmbedderAndLimit() throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("hash-v1-256|10|running shoes".getBytes(StandardCharsets.UTF_8)));
        String expected = "chatads:candidates:v1:" + hash;
        assertEquals(expected, CachingRetriever.key("hash-v1-256", "  Running  Shoes! ", 10));
        assertEquals(expected, CachingRetriever.key("hash-v1-256", "running\tshoes", 10));
        assertNotEquals(expected, CachingRetriever.key("hash-v1-256", "running shoes", 3));
        assertNotEquals(expected, CachingRetriever.key("hash-v1-64", "running shoes", 10));
    }

    @Test
    void normalizationDoesNotDependOnTheDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertEquals(CachingRetriever.key("model", "running shoes", 10),
                    CachingRetriever.key("model", "RUNNING SHOES", 10));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void countersExistBeforeTheFirstRequest() {
        assertEquals(0, count("hit"));
        assertEquals(0, count("miss"));
        assertEquals(0, count("error"));
    }

    @Test
    void missThenHitCallsTheDelegateOnceAndRestoresTheCatalogProduct() {
        CachingRetriever retriever = caching(delegate);
        assertSame(candidates, retriever.retrieve("Running  Shoes!", 10));
        List<ScoredProduct> hit = retriever.retrieve("running shoes", 10);
        assertEquals(candidates, hit);
        assertSame(catalog.find("p-1001").orElseThrow(), hit.getFirst().product());
        assertEquals(1, calls.get());
        assertEquals(1, count("miss"));
        assertEquals(1, count("hit"));
        assertEquals(0, count("error"));
        assertEquals(Duration.ofSeconds(2), store.ttl);
        assertEquals("[{\"id\":\"p-1001\",\"score\":2.5,\"matched\":[\"running\",\"shoe\"]}]",
                store.values.values().iterator().next());
    }

    @Test
    void unknownIdMakesTheWholeEntryAMissAndOverwritesIt() {
        String key = CachingRetriever.key("hash-v1-256", "running shoes", 10);
        store.values.put(key, "[{\"id\":\"p-1001\",\"score\":1,\"matched\":[]},"
                + "{\"id\":\"removed\",\"score\":4,\"matched\":[]}]");
        CachingRetriever retriever = caching(delegate);
        assertEquals(candidates, retriever.retrieve("running shoes", 10));
        assertEquals(candidates, retriever.retrieve("running shoes", 10));
        assertEquals(1, calls.get());
        assertEquals(1, count("miss"));
        assertEquals(1, count("hit"));
        assertEquals(0, count("error"));
        assertTrue(!store.values.get(key).contains("removed"));
    }

    @Test
    void emptyCandidateListsAreCacheHitsToo() {
        CachingRetriever retriever = caching((prompt, limit) -> {
            calls.incrementAndGet();
            return List.of();
        });
        assertEquals(List.of(), retriever.retrieve("unmatched", 10));
        assertEquals(List.of(), retriever.retrieve("unmatched", 10));
        assertEquals(1, calls.get());
        assertEquals(1, count("hit"));
    }

    @Test
    void redisReadFailureCallsTheDelegateAndCountsOneError() {
        store.failRead = true;
        assertSame(candidates, caching(delegate).retrieve("running shoes", 10));
        assertEquals(1, calls.get());
        assertEquals(1, count("error"));
        assertEquals(0, count("miss"));
        assertEquals(1, registry.get("chatads.fallback").tag("reason", "cache_error").counter().count());
        assertTrue(store.values.isEmpty());
    }

    @Test
    void redisWriteFailureReturnsTheRetrievedCandidatesAndCountsOneError() {
        store.failWrite = true;
        assertSame(candidates, caching(delegate).retrieve("running shoes", 10));
        assertEquals(1, calls.get());
        assertEquals(1, count("miss"));
        assertEquals(1, count("error"));
        assertEquals(1, registry.get("chatads.fallback").tag("reason", "cache_error").counter().count());
    }

    @Test
    void malformedJsonFallsThroughWithoutServingCachedData() {
        store.values.put(CachingRetriever.key("hash-v1-256", "running shoes", 10), "broken");
        assertSame(candidates, caching(delegate).retrieve("running shoes", 10));
        assertEquals(1, calls.get());
        assertEquals(1, count("error"));
        assertEquals(0, count("hit"));
    }

    @Test
    void delegateFailuresPropagateWithoutBeingCountedAsCacheErrorsOrStored() {
        IllegalStateException failure = new IllegalStateException("backend unavailable");
        CachingRetriever retriever = caching((prompt, limit) -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> retriever.retrieve("x", 10)));
        assertEquals(0, count("error"));
        assertTrue(store.values.isEmpty());
    }

    @Test
    void ttlMustBePositive() {
        for (Duration ttl : List.of(Duration.ZERO, Duration.ofSeconds(-1))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CachingRetriever(delegate, catalog, store, "model", ttl, metrics));
        }
    }

    private static class MemoryStore implements CachingRetriever.Store {
        final Map<String, String> values = new HashMap<>();
        Duration ttl;
        boolean failRead;
        boolean failWrite;

        @Override
        public String get(String key) {
            if (failRead) {
                throw new DataAccessResourceFailureException("store unavailable");
            }
            return values.get(key);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            if (failWrite) {
                throw new DataAccessResourceFailureException("store unavailable");
            }
            this.ttl = ttl;
            values.put(key, value);
        }
    }
}
