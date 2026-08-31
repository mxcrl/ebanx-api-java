package com.ebanx;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the rate limiter to a tiny budget and confirms that requests
 * past it get 429 with a Retry-After header, while requests within
 * budget still reach the controller.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "ratelimit.capacity=3",
                "ratelimit.refill-period-seconds=60"
        })
class RateLimitTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void requestsBeyondTheBudgetAreRejectedWith429() {
        int ok = 0;
        int limited = 0;
        for (int i = 0; i < 10; i++) {
            ResponseEntity<String> res = restTemplate.getForEntity("/balance?account_id=1", String.class);
            if (res.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                limited++;
                assertThat(res.getHeaders().getFirst("Retry-After")).isEqualTo("1");
            } else {
                ok++;
            }
        }

        assertThat(ok).isBetween(1, 4);
        assertThat(limited).isGreaterThanOrEqualTo(6);
    }
}
