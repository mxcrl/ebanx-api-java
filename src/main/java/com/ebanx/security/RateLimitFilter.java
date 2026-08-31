package com.ebanx.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token-bucket rate limiter, keyed by client IP. Each key
 * gets {@code capacity} tokens that refill at a steady rate; a request
 * with no token available is rejected with 429 before it reaches the
 * controller. Runs ahead of the Spring Security chain so that
 * unauthenticated floods are cheap to shed.
 *
 * State lives in a single process. Behind more than one instance this
 * becomes per-instance limiting - fine as a safety valve, not a quota.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final long capacity;
    private final double tokensPerNano;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(long capacity, long refillPeriodSeconds) {
        this.capacity = capacity;
        this.tokensPerNano = (double) capacity / (refillPeriodSeconds * 1_000_000_000L);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientKey = clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(clientKey, key -> new Bucket(capacity, System.nanoTime()));

        if (bucket.tryConsume(tokensPerNano, capacity)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rate limit exceeded for {} on {} {}", clientKey, request.getMethod(), request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, "1");
        response.getWriter().write("{\"error\":\"Too Many Requests\"}");
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Lazily-refilled token bucket. {@code synchronized} because the
     * refill-then-consume pair must be atomic per key; contention is
     * per-IP so this is not a global lock.
     */
    private static final class Bucket {

        private double tokens;
        private long lastRefillNanos;

        Bucket(double tokens, long nowNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = nowNanos;
        }

        synchronized boolean tryConsume(double tokensPerNano, long capacity) {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefillNanos) * tokensPerNano);
            lastRefillNanos = now;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }
}
