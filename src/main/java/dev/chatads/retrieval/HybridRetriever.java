package dev.chatads.retrieval;

import dev.chatads.embedding.Embedder;
import dev.chatads.embedding.EmbeddingResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full-text and vector search over the same catalog, merged with reciprocal rank fusion.
 *
 * <p>Each side has its own gate, because a nearest-neighbour query always returns something and
 * a loose full-text OR query matches on a single common word. A full-text hit counts only if the
 * prompt terms it matched are worth {@value KeywordRetriever#MIN_SCORE} points (the same rule the
 * in-memory retriever uses). A vector hit counts only if its cosine similarity reaches
 * {@code minSimilarity} and stays within {@code relativeCutoff} of the best vector hit, which keeps the
 * weak tail of a nearest-neighbour list out. Products that pass either gate are fused; ones that
 * pass both rise to the top.
 */
public class HybridRetriever implements Retriever {

    private final ProductIndex index;
    private final Embedder embedder;
    private final int candidates;
    private final int rrfK;
    private final double minSimilarity;
    private final double relativeCutoff;

    public HybridRetriever(ProductIndex index, Embedder embedder, int candidates, int rrfK, double minSimilarity,
                           double relativeCutoff) {
        this.index = index;
        this.embedder = embedder;
        this.candidates = candidates;
        this.rrfK = rrfK;
        this.minSimilarity = minSimilarity;
        this.relativeCutoff = relativeCutoff;
    }

    @Override
    public List<ScoredProduct> retrieve(String prompt, int limit) {
        Set<String> terms = Tokenizer.terms(prompt);
        Map<String, Product> products = new HashMap<>();
        Map<String, List<String>> matched = new HashMap<>();

        List<String> keywordRanked = new ArrayList<>();
        if (!terms.isEmpty()) {
            for (ProductIndex.Hit hit : index.keywordSearch(terms, candidates)) {
                ProductTerms.Match m = ProductTerms.of(hit.product()).score(terms);
                if (m.points() >= KeywordRetriever.MIN_SCORE) {
                    keywordRanked.add(hit.product().id());
                    products.put(hit.product().id(), hit.product());
                    matched.put(hit.product().id(), m.terms());
                }
            }
        }

        List<String> vectorRanked = new ArrayList<>();
        EmbeddingResult query = embedder.embed(prompt);
        if (!query.isZero()) {
            List<ProductIndex.Hit> nearest = index.vectorSearch(query.vector(), candidates);
            double floor = nearest.isEmpty() ? 0 : Math.max(minSimilarity, nearest.get(0).score() * relativeCutoff);
            for (ProductIndex.Hit hit : nearest) {
                if (hit.score() >= floor) {
                    vectorRanked.add(hit.product().id());
                    products.putIfAbsent(hit.product().id(), hit.product());
                }
            }
        }

        Map<String, Double> fused = ReciprocalRankFusion.fuse(List.of(keywordRanked, vectorRanked), rrfK);
        List<ScoredProduct> out = new ArrayList<>();
        fused.forEach((id, score) -> out.add(new ScoredProduct(products.get(id), score, matched.getOrDefault(id, List.of()))));
        out.sort(Comparator.comparingDouble(ScoredProduct::score).reversed().thenComparing(s -> s.product().id()));
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : out;
    }
}
