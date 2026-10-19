package dev.chatads.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.chatads.embedding.HashingEmbedder;
import dev.chatads.support.Containers;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIf(value = "dev.chatads.support.Containers#dockerAvailable", disabledReason = "Docker is not available")
class PostgresProductIndexTest {

    static JdbcTemplate jdbc;

    @BeforeAll
    static void connect() {
        var pg = Containers.postgres();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
        jdbc.execute("DROP TABLE IF EXISTS products");
    }

    private static PostgresProductIndex index(Catalog catalog, int dims) {
        return new PostgresProductIndex(jdbc, catalog, new HashingEmbedder(dims));
    }

    @Test
    void loadsTheCatalogAndAnswersFullTextQueries() {
        Catalog catalog = Catalog.loadDefault();
        PostgresProductIndex index = index(catalog, 256);
        index.ensureReady();

        assertEquals(catalog.products().size(), jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));

        List<ProductIndex.Hit> hits = index.keywordSearch(Set.of("running", "shoe"), 5);
        assertEquals("p-1001", hits.get(0).product().id());
        assertTrue(hits.get(0).score() > 0);
        assertEquals("footwear", hits.get(0).product().category());
        assertTrue(hits.get(0).product().keywords().contains("marathon"));
    }

    @Test
    void titleMatchesOutrankKeywordMatches() {
        PostgresProductIndex index = index(Catalog.loadDefault(), 256);
        List<ProductIndex.Hit> hits = index.keywordSearch(Set.of("headphone"), 10);
        // "headphones" is in the title of p-1201 and only a keyword of nothing else in electronics.
        assertEquals("p-1201", hits.get(0).product().id());
    }

    @Test
    void vectorSearchFindsTheNearestProductEvenWithATypo() {
        PostgresProductIndex index = index(Catalog.loadDefault(), 256);
        var embedder = new HashingEmbedder(256);
        List<ProductIndex.Hit> hits = index.vectorSearch(embedder.embed("runing shose").vector(), 3);
        assertEquals("p-1001", hits.get(0).product().id());
        assertTrue(hits.get(0).score() > hits.get(2).score() - 1e-9);
    }

    @Test
    void loadingTwiceKeepsOneRowPerProduct() {
        Catalog catalog = Catalog.loadDefault();
        index(catalog, 256).ensureReady();
        index(catalog, 256).ensureReady();
        assertEquals(catalog.products().size(), jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));
    }

    @Test
    void rowsThatLeftTheCatalogAreDeletedAndANewEmbedderSizeRebuildsTheTable() {
        Catalog full = Catalog.loadDefault();
        index(full, 256).ensureReady();

        Catalog small = new Catalog(full.products().subList(0, 3));
        index(small, 256).ensureReady();
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM products", Integer.class));

        PostgresProductIndex resized = index(small, 64);
        resized.ensureReady();
        assertFalse(resized.vectorSearch(new HashingEmbedder(64).embed("running shoes").vector(), 3).isEmpty());
        index(full, 256).ensureReady();
    }
}
