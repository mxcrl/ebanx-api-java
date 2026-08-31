package com.ebanx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test suite exercising the server exactly the way the
 * grading test suite (and the assignment's example scenarios) would.
 * A real embedded server on a random port, real HTTP requests, no
 * mocks - so a green run here means the actual wire behaviour is
 * correct, not just some internal call path.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // give the rate limiter plenty of headroom so the functional suite never trips it
        properties = {
                "ratelimit.capacity=100000",
                "ratelimit.refill-period-seconds=1"
        })
class EndToEndTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void reset() {
        restTemplate.postForEntity("/reset", new HttpEntity<>(""), String.class);
    }

    @Test
    void balanceForNonExistingAccountIsNotFound() {
        ResponseEntity<String> res = restTemplate.getForEntity("/balance?account_id=1234", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).isEqualTo("0");
    }

    @Test
    void balanceWithoutAccountIdIsBadRequest() {
        ResponseEntity<String> res = restTemplate.getForEntity("/balance", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void emptyBodyToEventIsBadRequest() {
        ResponseEntity<String> res = postEvent("");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void plainTextEndpointsAnswerEvenWhenClientAsksForJson() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> reset = restTemplate.exchange(
                "/reset", HttpMethod.POST, new HttpEntity<>(headers), String.class);
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reset.getBody()).isEqualTo("OK");

        ResponseEntity<String> balance = restTemplate.exchange(
                "/balance?account_id=1234", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(balance.getBody()).isEqualTo("0");
    }

    @Test
    void depositCreatesAnAccount() {
        ResponseEntity<String> res = postEvent("{\"type\":\"deposit\",\"destination\":\"100\",\"amount\":10}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).isEqualTo("{\"destination\":{\"id\":\"100\",\"balance\":10}}");
    }

    @Test
    void depositIntoExistingAccountAccumulates() {
        postEvent("{\"type\":\"deposit\",\"destination\":\"100\",\"amount\":10}");
        ResponseEntity<String> res = postEvent("{\"type\":\"deposit\",\"destination\":\"100\",\"amount\":10}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).isEqualTo("{\"destination\":{\"id\":\"100\",\"balance\":20}}");
    }

    @Test
    void balanceForExistingAccountIsReturned() {
        postEvent("{\"type\":\"deposit\",\"destination\":\"100\",\"amount\":20}");
        ResponseEntity<String> res = restTemplate.getForEntity("/balance?account_id=100", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isEqualTo("20");
    }

    @Test
    void withdrawFromNonExistingAccountIsNotFound() {
        ResponseEntity<String> res = postEvent("{\"type\":\"withdraw\",\"origin\":\"200\",\"amount\":10}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).isEqualTo("0");
    }

    @Test
    void withdrawFromExistingAccountDebitsBalance() {
        postEvent("{\"type\":\"deposit\",\"destination\":\"100\",\"amount\":20}");
        ResponseEntity<String> res = postEvent("{\"type\":\"withdraw\",\"origin\":\"100\",\"amount\":5}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).isEqualTo("{\"origin\":{\"id\":\"100\",\"balance\":15}}");
    }

    @Test
    void transferBetweenExistingAccounts() {
        postEvent("{\"type\":\"deposit\",\"destination\":\"100\",\"amount\":15}");
        ResponseEntity<String> res = postEvent(
                "{\"type\":\"transfer\",\"origin\":\"100\",\"amount\":15,\"destination\":\"300\"}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).isEqualTo(
                "{\"origin\":{\"id\":\"100\",\"balance\":0},\"destination\":{\"id\":\"300\",\"balance\":15}}");
    }

    @Test
    void transferFromNonExistingAccountIsNotFound() {
        ResponseEntity<String> res = postEvent(
                "{\"type\":\"transfer\",\"origin\":\"200\",\"amount\":15,\"destination\":\"300\"}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).isEqualTo("0");
    }

    @Test
    void withdrawingMoreThanBalancePushesItNegative() {
        postEvent("{\"type\":\"deposit\",\"destination\":\"400\",\"amount\":5}");
        ResponseEntity<String> res = postEvent("{\"type\":\"withdraw\",\"origin\":\"400\",\"amount\":20}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).isEqualTo("{\"origin\":{\"id\":\"400\",\"balance\":-15}}");
    }

    @Test
    void malformedJsonBodyIsBadRequest() {
        ResponseEntity<String> res = postEvent("{not valid json");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownEventTypeIsBadRequest() {
        ResponseEntity<String> res = postEvent("{\"type\":\"yeet\",\"origin\":\"100\",\"amount\":5}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingAmountIsBadRequest() {
        ResponseEntity<String> res = postEvent("{\"type\":\"deposit\",\"destination\":\"100\"}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void missingRequiredFieldIsBadRequest() {
        ResponseEntity<String> res = postEvent("{\"type\":\"withdraw\",\"amount\":5}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void nonIntegerAmountIsBadRequest() {
        ResponseEntity<String> res = postEvent("{\"type\":\"deposit\",\"destination\":\"100\",\"amount\":10.5}");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<String> postEvent(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/event", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }
}
