package com.ebanx.http;

import com.ebanx.domain.AccountService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;

/**
 * GET /balance?account_id=100
 *  -> 200 <balance>   if the account exists (plain number body)
 *  -> 404 0            if it doesn't
 *  -> 400              if account_id is missing entirely
 */
public final class BalanceHandler implements HttpHandler {

    private final AccountService accountService;

    public BalanceHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                HttpSupport.sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            Map<String, String> query = QueryParser.parse(exchange.getRequestURI());
            String accountId = query.get("account_id");

            if (accountId == null || accountId.isBlank()) {
                HttpSupport.sendText(exchange, 400, "Missing account_id query parameter");
                return;
            }

            Long balance = accountService.getBalance(accountId);
            if (balance == null) {
                HttpSupport.sendText(exchange, 404, "0");
            } else {
                HttpSupport.sendText(exchange, 200, String.valueOf(balance));
            }
        } catch (Exception unexpected) {
            HttpSupport.sendText(exchange, 500, "Internal Server Error");
        } finally {
            exchange.close();
        }
    }
}
