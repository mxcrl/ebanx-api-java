package com.ebanx.http;

import com.ebanx.domain.AccountRepository;
import com.ebanx.domain.AccountService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Composition root: wires the domain layer to the HTTP layer and
 * returns a server that's built but not yet started. Separated from
 * Main so tests can build a server on an ephemeral port (0) without
 * going through a separate process.
 */
public final class ServerFactory {

    private ServerFactory() {
    }

    public static HttpServer create(int port) throws IOException {
        AccountRepository accountRepository = new AccountRepository();
        AccountService accountService = new AccountService(accountRepository);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        Router.registerRoutes(server, accountService);

        // Virtual threads (JDK 21): each request gets its own cheap
        // thread instead of competing for a fixed pool, so a slow or
        // stuck request can't starve the rest of the server.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        return server;
    }
}
