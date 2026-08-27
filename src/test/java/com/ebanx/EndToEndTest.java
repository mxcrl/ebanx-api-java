package com.ebanx;

import com.ebanx.http.ServerFactory;
import com.sun.net.httpserver.HttpServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * End-to-end test suite exercising the server exactly the way the
 * grading test suite (and the assignment's example scenarios) would.
 * Deliberately written with zero external dependencies - a real HTTP
 * server started on an ephemeral port, real HTTP requests, no mocks -
 * so a green run here means the actual wire behaviour is correct, not
 * just some internal call path.
 *
 * Run with: java -cp out com.ebanx.EndToEndTest
 */
public final class EndToEndTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        HttpServer server = ServerFactory.create(0);
        server.start();
        String base = "http://localhost:" + server.getAddress().getPort();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        try {
            reset(client, base);

            check("GET /balance for a non-existing account -> 404 / 0", () -> {
                HttpResponse<String> res = get(client, base + "/balance?account_id=1234");
                assertEquals(404, res.statusCode());
                assertEquals("0", res.body());
            });

            check("deposit creates an account", () -> {
                HttpResponse<String> res = post(client, base + "/event",
                        "{\"type\":\"deposit\",\"destination\":\"100\",\"amount\":10}");
                assertEquals(201, res.statusCode());
                assertEquals("{\"destination\":{\"id\":\"100\",\"balance\":10}}", res.body());
            });

            check("deposit into an existing account accumulates", () -> {
                HttpResponse<String> res = post(client, base + "/event",
                        "{\"type\":\"deposit\",\"destination\":\"100\",\"amount\":10}");
                assertEquals(201, res.statusCode());
                assertEquals("{\"destination\":{\"id\":\"100\",\"balance\":20}}", res.body());
            });

            check("GET /balance for an existing account -> 200 / balance", () -> {
                HttpResponse<String> res = get(client, base + "/balance?account_id=100");
                assertEquals(200, res.statusCode());
                assertEquals("20", res.body());
            });

            check("withdraw from a non-existing account -> 404 / 0", () -> {
                HttpResponse<String> res = post(client, base + "/event",
                        "{\"type\":\"withdraw\",\"origin\":\"200\",\"amount\":10}");
                assertEquals(404, res.statusCode());
                assertEquals("0", res.body());
            });

            check("withdraw from an existing account debits the balance", () -> {
                HttpResponse<String> res = post(client, base + "/event",
                        "{\"type\":\"withdraw\",\"origin\":\"100\",\"amount\":5}");
                assertEquals(201, res.statusCode());
                assertEquals("{\"origin\":{\"id\":\"100\",\"balance\":15}}", res.body());
            });

            check("transfer between existing accounts", () -> {
                HttpResponse<String> res = post(client, base + "/event",
                        "{\"type\":\"transfer\",\"origin\":\"100\",\"amount\":15,\"destination\":\"300\"}");
                assertEquals(201, res.statusCode());
                assertEquals(
                        "{\"origin\":{\"id\":\"100\",\"balance\":0},\"destination\":{\"id\":\"300\",\"balance\":15}}",
                        res.body());
            });

            check("transfer from a non-existing account -> 404 / 0", () -> {
                HttpResponse<String> res = post(client, base + "/event",
                        "{\"type\":\"transfer\",\"origin\":\"200\",\"amount\":15,\"destination\":\"300\"}");
                assertEquals(404, res.statusCode());
                assertEquals("0", res.body());
            });

            // --- extra cases beyond the original example scenarios ---

            check("withdrawing more than the balance pushes it negative", () -> {
                reset(client, base);
                post(client, base + "/event", "{\"type\":\"deposit\",\"destination\":\"400\",\"amount\":5}");
                HttpResponse<String> res = post(client, base + "/event",
                        "{\"type\":\"withdraw\",\"origin\":\"400\",\"amount\":20}");
                assertEquals(201, res.statusCode());
                assertEquals("{\"origin\":{\"id\":\"400\",\"balance\":-15}}", res.body());
            });

            check("malformed JSON body -> 400", () -> {
                HttpResponse<String> res = post(client, base + "/event", "{not valid json");
                assertEquals(400, res.statusCode());
            });

            check("unknown event type -> 400", () -> {
                HttpResponse<String> res = post(client, base + "/event",
                        "{\"type\":\"yeet\",\"origin\":\"100\",\"amount\":5}");
                assertEquals(400, res.statusCode());
            });

            check("missing amount -> 400", () -> {
                HttpResponse<String> res = post(client, base + "/event",
                        "{\"type\":\"deposit\",\"destination\":\"100\"}");
                assertEquals(400, res.statusCode());
            });

            check("missing required field (origin for withdraw) -> 400", () -> {
                HttpResponse<String> res = post(client, base + "/event",
                        "{\"type\":\"withdraw\",\"amount\":5}");
                assertEquals(400, res.statusCode());
            });

            check("non-integer amount -> 400", () -> {
                HttpResponse<String> res = post(client, base + "/event",
                        "{\"type\":\"deposit\",\"destination\":\"100\",\"amount\":10.5}");
                assertEquals(400, res.statusCode());
            });
        } finally {
            server.stop(0);
        }

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void reset(HttpClient client, String base) throws Exception {
        post(client, base + "/reset", "");
    }

    private static HttpResponse<String> get(HttpClient client, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(HttpClient client, String url, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void check(String description, TestCase testCase) {
        try {
            testCase.run();
            passed++;
            System.out.println("PASS - " + description);
        } catch (AssertionError | Exception e) {
            failed++;
            System.out.println("FAIL - " + description + " (" + e.getMessage() + ")");
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    @FunctionalInterface
    private interface TestCase {
        void run() throws Exception;
    }
}
