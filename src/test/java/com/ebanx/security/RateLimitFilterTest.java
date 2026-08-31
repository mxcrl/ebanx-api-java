package com.ebanx.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private MockHttpServletResponse pass(RateLimitFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/balance");
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void allowsRequestsUpToCapacityThenRejectsWith429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(3, 60);

        for (int i = 0; i < 3; i++) {
            assertThat(pass(filter, request("1.1.1.1")).getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse limited = pass(filter, request("1.1.1.1"));
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("1");
        assertThat(limited.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void bucketsAreIndependentPerClientIp() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 60);

        assertThat(pass(filter, request("1.1.1.1")).getStatus()).isEqualTo(200);
        assertThat(pass(filter, request("1.1.1.1")).getStatus()).isEqualTo(429);
        // different IP still has its full budget
        assertThat(pass(filter, request("2.2.2.2")).getStatus()).isEqualTo(200);
    }

    @Test
    void usesFirstHopOfXForwardedForWhenPresent() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 60);

        MockHttpServletRequest a = request("10.0.0.1");
        a.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        MockHttpServletRequest b = request("10.0.0.2");
        b.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.2");

        assertThat(pass(filter, a).getStatus()).isEqualTo(200);
        // same forwarded client -> same bucket, even though remoteAddr differs
        assertThat(pass(filter, b).getStatus()).isEqualTo(429);
    }

    @Test
    void refillsOverTime() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1); // 1 token per second

        assertThat(pass(filter, request("9.9.9.9")).getStatus()).isEqualTo(200);
        assertThat(pass(filter, request("9.9.9.9")).getStatus()).isEqualTo(429);

        Thread.sleep(1100);
        assertThat(pass(filter, request("9.9.9.9")).getStatus()).isEqualTo(200);
    }
}
