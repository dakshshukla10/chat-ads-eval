package dev.chatads.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * In-memory keyword overlap between the prompt and each product. It is the fallback when the
 * hybrid retriever cannot reach Postgres, and the baseline the hybrid retriever is compared with.
 * A product needs at least {@value #MIN_SCORE} points (see {@link ProductTerms}) to be returned.
 */
public class KeywordRetriever implements Retriever {

    public static final int MIN_SCORE = 2;

    private record Indexed(Product product, ProductTerms terms) { }

    private final List<Indexed> index = new ArrayList<>();

    public KeywordRetriever(Catalog catalog) {
        for (Product p : catalog.products()) {
            index.add(new Indexed(p, ProductTerms.of(p)));
        }
    }

    @Override
    public List<ScoredProduct> retrieve(String prompt, int limit) {
        Set<String> promptTerms = Tokenizer.terms(prompt);
        List<ScoredProduct> hits = new ArrayList<>();
        for (Indexed entry : index) {
            ProductTerms.Match m = entry.terms().score(promptTerms);
            if (m.points() >= MIN_SCORE) {
                hits.add(new ScoredProduct(entry.product(), m.points(), m.terms()));
            }
        }
        hits.sort(Comparator.comparingDouble(ScoredProduct::score).reversed()
                .thenComparing(h -> h.product().id()));
        return hits.size() > limit ? List.copyOf(hits.subList(0, limit)) : hits;
    }
}
