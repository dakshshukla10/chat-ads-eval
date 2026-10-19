package dev.chatads.policy;

import dev.chatads.retrieval.ScoredProduct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Removes candidates whose category is on the banned list. */
public class PolicyFilter {

    private final Set<String> bannedCategories;

    public PolicyFilter(Set<String> bannedCategories) {
        this.bannedCategories = Set.copyOf(bannedCategories);
    }

    public static PolicyFilter withDefaults() {
        return new PolicyFilter(loadBannedCategories());
    }

    /** Reads {@code policy/banned-categories.txt} (one per line, {@code #} comments). */
    public static Set<String> loadBannedCategories() {
        try (InputStream in = PolicyFilter.class.getResourceAsStream("/policy/banned-categories.txt")) {
            if (in == null) {
                throw new IllegalStateException("policy/banned-categories.txt not found on classpath");
            }
            Set<String> out = new LinkedHashSet<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String s = line.strip().toLowerCase(Locale.ROOT);
                    if (!s.isEmpty() && !s.startsWith("#")) {
                        out.add(s);
                    }
                }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read banned categories", e);
        }
    }

    public Set<String> bannedCategories() {
        return bannedCategories;
    }

    public boolean isAllowed(ScoredProduct candidate) {
        return !bannedCategories.contains(candidate.product().category().toLowerCase(Locale.ROOT));
    }

    public List<ScoredProduct> apply(List<ScoredProduct> candidates) {
        return candidates.stream().filter(this::isAllowed).toList();
    }
}
