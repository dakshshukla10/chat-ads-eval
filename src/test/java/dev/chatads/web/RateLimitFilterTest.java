package dev.chatads.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    @Test
    void blocksTheCallAfterTheLimitAndReopensWhenTheWindowEnds() {
        RateLimitFilter filter = new RateLimitFilter(2, Clock.systemUTC());
        assertEquals(0, filter.tryAcquire("1.1.1.1", 0));
        assertEquals(0, filter.tryAcquire("1.1.1.1", 1_000));
        assertEquals(59, filter.tryAcquire("1.1.1.1", 1_000));
        assertEquals(0, filter.tryAcquire("2.2.2.2", 1_000), "another client has its own count");
        assertEquals(0, filter.tryAcquire("1.1.1.1", 60_000));
    }

    @Test
    void answers429WithRetryAfterOnlyForTheApiPath() throws Exception {
        Clock fixed = Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC);
        RateLimitFilter filter = new RateLimitFilter(1, fixed);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse page = new MockHttpServletResponse();
            filter.doFilter(request("/"), page, new MockFilterChain());
            assertEquals(200, page.getStatus(), "the landing page is never limited");
        }

        MockHttpServletResponse first = new MockHttpServletResponse();
        filter.doFilter(request("/v1/ads/contextual"), first, new MockFilterChain());
        assertEquals(200, first.getStatus());

        MockHttpServletResponse second = new MockHttpServletResponse();
        filter.doFilter(request("/v1/ads/contextual"), second, new MockFilterChain());
        assertEquals(429, second.getStatus());
        assertEquals("60", second.getHeader("Retry-After"));
        assertNotNull(second.getContentAsString());
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("203.0.113.9");
        return request;
    }
}
