package dev.chatads.retrieval;

import dev.chatads.embedding.Embedder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * The catalog in Postgres: a generated {@code tsvector} column for full-text search (title weighted
 * highest, then keywords, then category) and a pgvector column for similarity search.
 *
 * <p>The table is derived from {@code catalog.json}. {@link #ensureReady()} creates it, loads every
 * product and drops rows that left the file. A row is re-embedded only when its text or the
 * embedder changed. If the vector size no longer matches the embedder the table is rebuilt.
 */
public class PostgresProductIndex implements ProductIndex {

    private static final Logger log = LoggerFactory.getLogger(PostgresProductIndex.class);

    private final JdbcTemplate jdbc;
    private final Catalog catalog;
    private final Embedder embedder;
    private volatile boolean ready;

    public PostgresProductIndex(JdbcTemplate jdbc, Catalog catalog, Embedder embedder) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.embedder = embedder;
    }

    /** Idempotent and cheap once it has succeeded. Throws if Postgres cannot be reached. */
    public void ensureReady() {
        if (ready) {
            return;
        }
        synchronized (this) {
            if (ready) {
                return;
            }
            createSchema();
            load();
            ready = true;
        }
    }

    private void createSchema() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        Integer existingDims = jdbc.query(
                "SELECT a.atttypmod FROM pg_attribute a WHERE a.attrelid = to_regclass('products') "
                        + "AND a.attname = 'embedding' AND NOT a.attisdropped",
                rs -> rs.next() ? rs.getInt(1) : null);
        if (existingDims != null && existingDims != embedder.dimensions()) {
            log.info("Embedding size changed from {} to {}, rebuilding the products table", existingDims, embedder.dimensions());
            jdbc.execute("DROP TABLE products");
        }
        // keywords_text duplicates keywords because array_to_string is not immutable and a
        // generated column may only use immutable functions.
        jdbc.execute("CREATE TABLE IF NOT EXISTS products ("
                + "id text PRIMARY KEY, title text NOT NULL, category text NOT NULL, "
                + "keywords text[] NOT NULL, keywords_text text NOT NULL, "
                + "content_hash text NOT NULL, embedding vector(" + embedder.dimensions() + ") NOT NULL, "
                + "search_vec tsvector GENERATED ALWAYS AS ("
                + "setweight(to_tsvector('english', title), 'A') || "
                + "setweight(to_tsvector('english', keywords_text), 'B') || "
                + "setweight(to_tsvector('english', replace(category, '-', ' ')), 'C')) STORED)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS products_search_idx ON products USING gin (search_vec)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS products_embedding_idx ON products USING hnsw (embedding vector_cosine_ops)");
    }

    private void load() {
        Set<String> ids = new HashSet<>();
        int embedded = 0;
        for (Product p : catalog.products()) {
            ids.add(p.id());
            String hash = contentHash(p);
            String stored = jdbc.query("SELECT content_hash FROM products WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null, p.id());
            if (hash.equals(stored)) {
                continue;
            }
            float[] vector = embedder.embed(embedText(p)).vector();
            jdbc.update("INSERT INTO products (id, title, category, keywords, keywords_text, content_hash, embedding) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?::vector) "
                            + "ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title, category = EXCLUDED.category, "
                            + "keywords = EXCLUDED.keywords, keywords_text = EXCLUDED.keywords_text, "
                            + "content_hash = EXCLUDED.content_hash, embedding = EXCLUDED.embedding",
                    p.id(), p.title(), p.category(), p.keywords().toArray(new String[0]),
                    String.join(" ", p.keywords()), hash, vectorLiteral(vector));
            embedded++;
        }
        List<String> stale = jdbc.queryForList("SELECT id FROM products", String.class).stream()
                .filter(id -> !ids.contains(id)).toList();
        stale.forEach(id -> jdbc.update("DELETE FROM products WHERE id = ?", id));
        log.info("Catalog in Postgres: {} products, {} embedded this start, {} removed", ids.size(), embedded, stale.size());
    }

    static String embedText(Product p) {
        return p.title() + " " + String.join(" ", p.keywords()) + " " + p.category().replace('-', ' ');
    }

    private String contentHash(Product p) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String text = embedder.id() + "|" + p.title() + "|" + p.category() + "|" + String.join(",", p.keywords());
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String vectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    private static Product product(ResultSet rs) throws SQLException {
        Array keywords = rs.getArray("keywords");
        return new Product(rs.getString("id"), rs.getString("title"), rs.getString("category"),
                Arrays.asList((String[]) keywords.getArray()));
    }

    private static final RowMapper<Hit> HIT = (rs, i) -> new Hit(product(rs), rs.getDouble("score"));

    @Override
    public List<Hit> keywordSearch(Collection<String> terms, int limit) {
        ensureReady();
        // Terms come from Tokenizer, so they are [a-z0-9] only and safe to join into a tsquery.
        String query = String.join(" | ", terms);
        return jdbc.query("SELECT id, title, category, keywords, ts_rank_cd(search_vec, q) AS score "
                + "FROM products, to_tsquery('english', ?) q WHERE search_vec @@ q "
                + "ORDER BY score DESC, id LIMIT ?", HIT, query, limit);
    }

    @Override
    public List<Hit> vectorSearch(float[] query, int limit) {
        ensureReady();
        String literal = vectorLiteral(query);
        return jdbc.query("SELECT id, title, category, keywords, 1 - (embedding <=> ?::vector) AS score "
                + "FROM products ORDER BY embedding <=> ?::vector, id LIMIT ?", HIT, literal, literal, limit);
    }
}
