package io.github.elderpath_crusade.server.launcher;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.BooleanSupplier;

/**
 * A tiny HTTP server for external health checks (e.g. a Kubernetes {@code tcpSocket}/HTTP probe),
 * separate from the game's own TCP transport. Two endpoints, both GET, body-less:
 * <ul>
 *   <li>{@code /healthz/live} — 200 while the match loop is still ticking, 503 if it looks stuck
 *       (no tick recently) — a liveness probe failing this should restart the process.</li>
 *   <li>{@code /healthz/ready} — 200 while still accepting/playing, 503 once shutting down — a
 *       readiness probe failing this should stop routing new connections here, without killing it.</li>
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
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
        http.createContext("/healthz/live", exchange -> respond(exchange, live.getAsBoolean()));
        http.createContext("/healthz/ready", exchange -> respond(exchange, ready.getAsBoolean()));
        http.setExecutor(null); // default executor is fine for two near-instant handlers
        http.start();
        return new HealthServer(http);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, boolean healthy) throws IOException {
        byte[] body = (healthy ? "OK" : "UNHEALTHY").getBytes();
        exchange.sendResponseHeaders(healthy ? 200 : 503, body.length);
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
