package dev.chatads.retrieval;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lower-cases, drops stop words and applies a naive plural stem. Postgres applies its own English
 * stemming on top when these terms go into a full-text query.
 */
public final class Tokenizer {

    private static final Set<String> STOPWORDS = Set.copyOf(java.util.List.of(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "at", "by", "for", "from", "with", "as",
            "is", "are", "was", "be", "do", "does", "it", "its", "i", "me", "my", "you", "your", "we",
            "that", "this", "these", "those", "some", "any", "into", "over", "about", "while", "has", "have",
            "get", "like", "one", "can", "could", "would", "should", "what", "which", "where", "how", "who",
            "need", "want", "looking", "buy", "buying", "recommend", "best", "good", "great", "new", "make",
            "better", "help", "please", "worth", "home", "set", "kit", "starter", "pro", "than", "then",
            "first", "small", "even", "gives", "give", "keeps", "has", "there", "here", "online"));

    private Tokenizer() {
    }

    public static Set<String> terms(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() < 2 || STOPWORDS.contains(raw)) {
                continue;
            }
            out.add(stem(raw));
        }
        return out;
    }

    public static String stem(String t) {
        if (t.length() > 3 && t.endsWith("s") && !t.endsWith("ss")) {
            return t.substring(0, t.length() - 1);
        }
        return t;
    }
}
