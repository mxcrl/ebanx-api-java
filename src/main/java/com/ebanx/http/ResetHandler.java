package com.ebanx.http;

import com.ebanx.domain.AccountService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * POST /reset - wipes all state. Always 200 OK.
 */
public final class ResetHandler implements HttpHandler {

    private final AccountService accountService;

    public ResetHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpSupport.sendText(exchange, 405, "Method Not Allowed");
                return;
            }
            accountService.reset();
            HttpSupport.sendText(exchange, 200, "OK");
        } catch (Exception unexpected) {
            // A bug here should never take the whole server down or hang
            // the client - always answer with something, even if it's a 500.
            HttpSupport.sendText(exchange, 500, "Internal Server Error");
        } finally {
            exchange.close();
        }
    }
}
