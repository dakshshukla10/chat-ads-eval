package dev.chatads.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 1 stub: plain keyword overlap between the prompt and each product.
 * A title match counts 2, a keyword or category match counts 1, and a product
 * needs a total of at least {@value #MIN_SCORE} to be returned.
 * There is no vector search yet (planned for Phase 2).
 */
public class KeywordRetriever implements Retriever {

    static final int MIN_SCORE = 2;

    private record Indexed(Product product, Set<String> titleTerms, Set<String> otherTerms) { }

    private final List<Indexed> index = new ArrayList<>();

    public KeywordRetriever(Catalog catalog) {
        for (Product p : catalog.products()) {
            Set<String> title = Tokenizer.terms(p.title());
            Set<String> other = new HashSet<>(Tokenizer.terms(String.join(" ", p.keywords())));
            other.addAll(Tokenizer.terms(p.category().replace('-', ' ')));
            index.add(new Indexed(p, title, other));
        }
    }

    @Override
    public List<ScoredProduct> retrieve(String prompt, int limit) {
        Set<String> promptTerms = Tokenizer.terms(prompt);
        List<ScoredProduct> hits = new ArrayList<>();
        for (Indexed entry : index) {
            int score = 0;
            List<String> matched = new ArrayList<>();
            for (String term : promptTerms) {
                if (entry.titleTerms().contains(term)) {
                    score += 2;
                    matched.add(term);
                } else if (entry.otherTerms().contains(term)) {
                    score += 1;
                    matched.add(term);
                }
            }
            if (score >= MIN_SCORE) {
                hits.add(new ScoredProduct(entry.product(), score, matched));
            }
        }
        hits.sort(Comparator.comparingInt(ScoredProduct::score).reversed()
                .thenComparing(h -> h.product().id()));
        return hits.size() > limit ? List.copyOf(hits.subList(0, limit)) : hits;
    }
}
