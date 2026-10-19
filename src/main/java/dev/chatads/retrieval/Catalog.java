package dev.chatads.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory product catalog loaded from {@code catalog.json} on the classpath. */
public final class Catalog {

    private final List<Product> products;
    private final Map<String, Product> byId = new LinkedHashMap<>();

    public Catalog(List<Product> products) {
        this.products = List.copyOf(products);
        for (Product p : this.products) {
            if (byId.put(p.id(), p) != null) {
                throw new IllegalArgumentException("Duplicate product id: " + p.id());
            }
        }
    }

    public static Catalog loadDefault() {
        try (InputStream in = Catalog.class.getResourceAsStream("/catalog.json")) {
            if (in == null) {
                throw new IllegalStateException("catalog.json not found on classpath");
            }
            List<Product> products = new ObjectMapper().readValue(in, new TypeReference<>() { });
            return new Catalog(products);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read catalog.json", e);
        }
    }

    public List<Product> products() {
        return products;
    }

    public Optional<Product> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<String> categoryOf(String id) {
        return find(id).map(Product::category);
    }
}
