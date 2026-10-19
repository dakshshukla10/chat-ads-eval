package dev.chatads.metrics;

import java.util.Locale;

/** Why a request left the normal path. The lowercase name is the {@code reason} tag on the fallback counter. */
public enum FallbackReason {
    /** Nothing in the catalog matched, so no ad was returned. */
    NO_MATCH,
    /** Candidates matched but the policy filter removed all of them, so no ad was returned. */
    POLICY_FILTERED,
    /** Postgres or the embedding provider failed; the in-memory keyword retriever answered instead. */
    BACKEND_ERROR,
    /** Redis failed; the request was served without the cache. */
    CACHE_ERROR;

    public String tag() {
        return name().toLowerCase(Locale.ROOT);
    }
}
