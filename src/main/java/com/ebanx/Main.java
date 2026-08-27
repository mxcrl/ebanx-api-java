package com.ebanx;

import com.ebanx.http.ServerFactory;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;

public final class Main {

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));

        HttpServer server = ServerFactory.create(port);
        server.start();

        System.out.println("EBANX API (Java) listening on port " + port);
    }
}
