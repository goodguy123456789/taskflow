package com.taskflow.scheduler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RemoteDispatchJobTest {
    @Test
    void sendsOneAuthenticatedPostAndReadsTheDispatchCount() throws Exception {
        var method = new AtomicReference<String>();
        var token = new AtomicReference<String>();
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/scheduler/dispatch-due", exchange -> {
            requests.incrementAndGet();
            method.set(exchange.getRequestMethod());
            token.set(exchange.getRequestHeaders().getFirst("X-Taskflow-Scheduler-Token"));
            byte[] body = "2".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            var job = new RemoteDispatchJob(url(server), "lab-token");
            assertEquals(2, job.dispatchOnce());
            assertEquals("POST", method.get());
            assertEquals("lab-token", token.get());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void nonSuccessResponseIsAFailure() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/scheduler/dispatch-due", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();
        try {
            var job = new RemoteDispatchJob(url(server), "lab-token");
            assertTrue(assertThrows(IOException.class, job::dispatchOnce).getMessage().contains("403"));
        } finally {
            server.stop(0);
        }
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/internal/scheduler/dispatch-due";
    }
}
