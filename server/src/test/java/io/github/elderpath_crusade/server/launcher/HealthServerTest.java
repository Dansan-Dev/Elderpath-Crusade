package io.github.elderpath_crusade.server.launcher;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The HTTP health endpoints ServerLauncher exposes for an external prober (e.g. a Kubernetes
 * liveness/readiness check) when {@code --health-port} is given — see ServerLauncher.
 */
class HealthServerTest {
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void live_reflectsTheSuppliedState() throws IOException, InterruptedException {
        AtomicBoolean live = new AtomicBoolean(true);
        try (HealthServer health = HealthServer.start(0, live::get, () -> true)) {
            assertEquals(200, get(health, "/healthz/live").statusCode());
            live.set(false);
            assertEquals(503, get(health, "/healthz/live").statusCode());
        }
    }

    @Test
    void ready_reflectsTheSuppliedState() throws IOException, InterruptedException {
        AtomicBoolean ready = new AtomicBoolean(true);
        try (HealthServer health = HealthServer.start(0, () -> true, ready::get)) {
            assertEquals(200, get(health, "/healthz/ready").statusCode());
            ready.set(false);
            assertEquals(503, get(health, "/healthz/ready").statusCode());
        }
    }

    @Test
    void anEphemeralPort_isReportedBack() throws IOException {
        try (HealthServer health = HealthServer.start(0, () -> true, () -> true)) {
            assertTrue(health.port() > 0);
        }
    }

    @Test
    void diag_servesTheSuppliedJsonWhenGiven() throws IOException, InterruptedException {
        try (HealthServer health = HealthServer.start(0, () -> true, () -> true, () -> "{\"tickCount\":42}")) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + health.port() + "/diag")).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("{\"tickCount\":42}", response.body());
            assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(null));
        }
    }

    @Test
    void diag_isNotRegisteredWithoutADiagnosticsSupplier() throws IOException, InterruptedException {
        try (HealthServer health = HealthServer.start(0, () -> true, () -> true)) {
            // No context registered for the path at all (not "unhealthy") — com.sun.net.httpserver
            // answers an unrouted path with a plain 404, same as any other unknown path would get.
            assertEquals(404, get(health, "/diag").statusCode());
        }
    }

    @Test
    void close_stopsItFromAnsweringFurtherRequests() throws IOException {
        HealthServer health = HealthServer.start(0, () -> true, () -> true);
        int port = health.port();
        health.close();

        assertThrows(IOException.class, () -> client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/healthz/live")).GET().build(),
                HttpResponse.BodyHandlers.discarding()));
    }

    private HttpResponse<Void> get(HealthServer health, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + health.port() + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.discarding());
    }
}
