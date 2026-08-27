package com.ebanx.http;

import com.ebanx.domain.AccountNotFoundException;
import com.ebanx.domain.AccountService;
import com.ebanx.domain.Event;
import com.ebanx.domain.EventParser;
import com.ebanx.domain.MalformedEventException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;

/**
 * POST /event - applies a deposit, withdraw, or transfer.
 *
 * This class's only job is: read the request, hand it to the domain
 * layer, translate the outcome into an HTTP response. It does not
 * contain any business rule itself - those all live in AccountService
 * and EventParser, which is what makes them reusable and unit-testable
 * without spinning up a server.
 */
public final class EventHandler implements HttpHandler {

    private final AccountService accountService;

    public EventHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpSupport.sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            Event event;
            try {
                event = parseEvent(HttpSupport.readBody(exchange));
            } catch (Json.JsonParseException | MalformedEventException badRequest) {
                HttpSupport.sendJson(exchange, 400,
                        Json.stringify(Map.of("error", String.valueOf(badRequest.getMessage()))));
                return;
            }

            try {
                AccountService.EventResult result = accountService.apply(event);
                HttpSupport.sendJson(exchange, 201, Json.stringify(result.toJson()));
            } catch (AccountNotFoundException notFound) {
                HttpSupport.sendText(exchange, 404, "0");
            }
        } catch (Exception unexpected) {
            // Catch-all so an unforeseen bug turns into a 500, never a
            // crashed server or a hung client connection.
            HttpSupport.sendJson(exchange, 500, Json.stringify(Map.of("error", "Internal Server Error")));
        } finally {
            exchange.close();
        }
    }

    private Event parseEvent(String rawBody) {
        Object parsed = Json.parse(rawBody);
        if (!(parsed instanceof Map<?, ?> rawMap)) {
            throw new MalformedEventException("Request body must be a JSON object");
        }

        Map<String, Object> json = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            json.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        return EventParser.parse(json);
    }
}
