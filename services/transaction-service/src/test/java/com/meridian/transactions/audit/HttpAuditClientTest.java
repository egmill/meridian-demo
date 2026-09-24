package com.meridian.transactions.audit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real HTTP client against a loopback stub of the audit-log
 * service, so no other Meridian service is contacted.
 */
class HttpAuditClientTest {

    private HttpServer server;
    private final AtomicInteger statusToReturn = new AtomicInteger(201);
    private final List<String> receivedBodies = new ArrayList<>();
    private final List<String> receivedPaths = new ArrayList<>();
    private final List<String> receivedMethods = new ArrayList<>();
    private final List<String> receivedContentTypes = new ArrayList<>();
    private HttpAuditClient client;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            receivedMethods.add(exchange.getRequestMethod());
            receivedPaths.add(exchange.getRequestURI().getPath());
            receivedContentTypes.add(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(statusToReturn.get(), -1);
            exchange.close();
        });
        server.start();
        client = new HttpAuditClient("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void record_posts_json_event_to_events_endpoint() {
        client.record(new AuditEvent("transaction-service", "TRANSFER", "TX-1001 ACC-1001 -> ACC-1002 amount=5.0 memo=x"));

        assertEquals(1, receivedBodies.size());
        assertEquals("POST", receivedMethods.get(0));
        assertEquals("/events", receivedPaths.get(0));
        assertTrue(receivedContentTypes.get(0).startsWith("application/json"), receivedContentTypes.get(0));
        String body = receivedBodies.get(0);
        assertTrue(body.contains("\"actor\":\"transaction-service\""), body);
        assertTrue(body.contains("\"action\":\"TRANSFER\""), body);
        assertTrue(body.contains("\"details\":\"TX-1001 ACC-1001 -> ACC-1002 amount=5.0 memo=x\""), body);
    }

    @Test
    void record_succeeds_on_200_as_well_as_201() {
        statusToReturn.set(200);
        client.record(new AuditEvent("a", "b", "c"));
        assertEquals(1, receivedBodies.size());
    }

    @Test
    void record_throws_when_audit_log_returns_4xx() {
        statusToReturn.set(400);

        RestClientException ex = assertThrows(HttpClientErrorException.class,
                () -> client.record(new AuditEvent("a", "b", "c")));
        assertTrue(ex.getMessage().startsWith("400"), ex.getMessage());
    }

    @Test
    void record_throws_when_audit_log_returns_5xx() {
        statusToReturn.set(503);

        assertThrows(HttpServerErrorException.class, () -> client.record(new AuditEvent("a", "b", "c")));
    }

    @Test
    void record_throws_when_audit_log_is_unreachable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        HttpAuditClient unreachable = new HttpAuditClient("http://127.0.0.1:" + closedPort);

        assertThrows(ResourceAccessException.class, () -> unreachable.record(new AuditEvent("a", "b", "c")));
    }

    @Test
    void each_record_call_results_in_exactly_one_request() {
        client.record(new AuditEvent("a", "b", "1"));
        client.record(new AuditEvent("a", "b", "2"));

        assertEquals(2, receivedBodies.size());
        assertTrue(receivedBodies.get(0).contains("\"details\":\"1\""));
        assertTrue(receivedBodies.get(1).contains("\"details\":\"2\""));
    }
}
