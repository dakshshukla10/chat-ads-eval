package dev.chatads.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A fixed one-minute window per client address on the {@code /v1/} endpoints. It lives in memory, so
 * each instance counts alone and a restart forgets everything, which is enough for a public demo.
 * Behind a proxy the address is only right when the servlet container resolves it from
 * X-Forwarded-For (see {@code server.forward-headers-strategy}).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED = 10_000;

    private record Window(long start, int count) { }

    private final int perMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(int perMinute, Clock clock) {
        if (perMinute < 1) {
            throw new IllegalArgumentException("perMinute must be positive");
        }
        this.perMinute = perMinute;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        long retryAfter = tryAcquire(request.getRemoteAddr(), now);
        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate limit reached, try again in " + retryAfter + " seconds\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Returns 0 when the call is allowed, otherwise the seconds until the window resets. */
    long tryAcquire(String client, long now) {
        if (windows.size() >= MAX_TRACKED) {
            evict(now);
        }
        long[] retry = {0};
        windows.compute(client, (k, w) -> {
            if (w == null || now - w.start() >= WINDOW_MILLIS) {
                return new Window(now, 1);
            }
            if (w.count() >= perMinute) {
                retry[0] = Math.max(1, (w.start() + WINDOW_MILLIS - now + 999) / 1000);
                return w;
            }
            return new Window(w.start(), w.count() + 1);
        });
        return retry[0];
    }

    private void evict(long now) {
        for (Iterator<Window> it = windows.values().iterator(); it.hasNext(); ) {
            if (now - it.next().start() >= WINDOW_MILLIS) {
                it.remove();
            }
        }
        // Still full of live windows means a flood from many addresses; start over rather than grow.
        if (windows.size() >= MAX_TRACKED) {
            windows.clear();
        }
    }
}
