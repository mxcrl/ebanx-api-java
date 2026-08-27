package com.ebanx.http;

import com.ebanx.domain.AccountService;
import com.sun.net.httpserver.HttpServer;

public final class Router {

    private Router() {
    }

    public static void registerRoutes(HttpServer server, AccountService accountService) {
        server.createContext("/reset", new ResetHandler(accountService));
        server.createContext("/balance", new BalanceHandler(accountService));
        server.createContext("/event", new EventHandler(accountService));
    }
}
