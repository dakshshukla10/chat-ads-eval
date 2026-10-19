package dev.chatads.retrieval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Searchable terms of one product, split by where they came from. A prompt term found in the
 * title is worth {@value #TITLE_POINTS} points, one found in the keywords or category is worth 1.
 */
public final class ProductTerms {

    public static final int TITLE_POINTS = 2;

    /** Points and the prompt terms behind them. */
    public record Match(int points, List<String> terms) { }

    private final Set<String> titleTerms;
    private final Set<String> otherTerms;

    private ProductTerms(Set<String> titleTerms, Set<String> otherTerms) {
        this.titleTerms = titleTerms;
        this.otherTerms = otherTerms;
    }

    public static ProductTerms of(Product p) {
        Set<String> other = new HashSet<>(Tokenizer.terms(String.join(" ", p.keywords())));
        other.addAll(Tokenizer.terms(p.category().replace('-', ' ')));
        return new ProductTerms(Tokenizer.terms(p.title()), other);
    }

    public Match score(Set<String> promptTerms) {
        int points = 0;
        List<String> matched = new ArrayList<>();
        for (String term : promptTerms) {
            if (titleTerms.contains(term)) {
                points += TITLE_POINTS;
                matched.add(term);
            } else if (otherTerms.contains(term)) {
                points += 1;
                matched.add(term);
            }
        }
        return new Match(points, matched);
    }
}
