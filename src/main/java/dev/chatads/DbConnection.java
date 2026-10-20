package dev.chatads;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Turns whatever the database host hands out into a JDBC url plus credentials. Neon and most other
 * hosts show a {@code postgresql://user:password@host/db?sslmode=require} string, which the Postgres
 * driver rejects. A {@code jdbc:} url passes through untouched.
 */
record DbConnection(String jdbcUrl, String user, String password) {

    static DbConnection parse(String url, String user, String password) {
        String trimmed = url.strip();
        if (trimmed.startsWith("jdbc:")) {
            return new DbConnection(trimmed, user, password);
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("chatads.db.url is neither a jdbc: url nor a postgresql:// url", e);
        }
        String scheme = uri.getScheme();
        if (!"postgres".equals(scheme) && !"postgresql".equals(scheme)) {
            throw new IllegalArgumentException("chatads.db.url must start with jdbc:postgresql:// or postgresql://");
        }
        String urlUser = user;
        String urlPassword = password;
        String info = uri.getRawUserInfo();
        if (info != null) {
            int colon = info.indexOf(':');
            String fromUrl = decode(colon < 0 ? info : info.substring(0, colon));
            // Explicit settings win, so the secret can still live in its own variable.
            if (user == null || user.isBlank()) {
                urlUser = fromUrl;
            }
            if ((password == null || password.isEmpty()) && colon >= 0) {
                urlPassword = decode(info.substring(colon + 1));
            }
        }
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getRawPath() == null ? "" : uri.getRawPath());
        // Neon adds channel_binding=require, which the JDBC driver spells differently and does not need here.
        String query = uri.getRawQuery() == null ? "" : Arrays.stream(uri.getRawQuery().split("&"))
                .filter(p -> !p.startsWith("channel_binding="))
                .collect(Collectors.joining("&"));
        if (!query.isEmpty()) {
            jdbc.append('?').append(query);
        }
        return new DbConnection(jdbc.toString(), urlUser, urlPassword);
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
