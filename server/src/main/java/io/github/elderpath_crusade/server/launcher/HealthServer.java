package io.github.elderpath_crusade.server.launcher;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A tiny HTTP server for external health checks and diagnostics (e.g. a Kubernetes {@code
 * tcpSocket}/HTTP probe, or a developer curling the pod), separate from the game's own TCP
 * transport. Up to three endpoints, all GET:
 * <ul>
 *   <li>{@code /healthz/live} — 200 while the match loop is still ticking, 503 if it looks stuck
 *       (no tick recently) — a liveness probe failing this should restart the process.</li>
 *   <li>{@code /healthz/ready} — 200 while still accepting/playing, 503 once shutting down — a
 *       readiness probe failing this should stop routing new connections here, without killing it.</li>
 *   <li>{@code /diag} — a JSON body of tick-timing stats (Sprint 64, {@link TickDiagnostics}),
 *       only registered when a diagnostics supplier is given; always 200 (there's no notion of
 *       "unhealthy" diagnostics, just data).</li>
 * </ul>
 * Uses the JDK's built-in {@code com.sun.net.httpserver} (no extra dependency). Not started unless
 * {@code --health-port} is given; disabled (port 0) leaves ServerLauncher's existing behavior untouched.
 */
final class HealthServer implements AutoCloseable {
    private final HttpServer http;

    private HealthServer(HttpServer http) {
        this.http = http;
    }

    static HealthServer start(int port, BooleanSupplier live, BooleanSupplier ready) throws IOException {
        return start(port, live, ready, null);
    }

    static HealthServer start(int port, BooleanSupplier live, BooleanSupplier ready, Supplier<String> diagnosticsJson) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/healthz/live", exchange -> respond(exchange, live.getAsBoolean()));
        http.createContext("/healthz/ready", exchange -> respond(exchange, ready.getAsBoolean()));
        if (diagnosticsJson != null) {
            http.createContext("/diag", exchange -> respondJson(exchange, diagnosticsJson.get()));
        }
        http.setExecutor(null); // default executor is fine for a handful of near-instant handlers
        http.start();
        return new HealthServer(http);
    }

    private static void respond(HttpExchange exchange, boolean healthy) throws IOException {
        byte[] body = (healthy ? "OK" : "UNHEALTHY").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(healthy ? 200 : 503, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void respondJson(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    int port() {
        return http.getAddress().getPort();
    }

    @Override
    public void close() {
        http.stop(0);
    }
}
