package io.github.elderpath_crusade.lobby;

/**
 * Where a lobby service is, parsed from what a player types: {@code host}, {@code host:port},
 * optionally prefixed {@code tls://} or {@code tcp://} to force the transport. Without a prefix a
 * remote host uses TLS on 443 (the public server's front proxy terminates it) and a local one
 * ({@code localhost}, 127.x.x.x) plain TCP on 4567 (a lobby service started by hand).
 */
public record LobbyEndpoint(String host, int port, boolean tls) {
    /** The dedicated server the game ships pointing at. */
    public static final String DEFAULT_ADDRESS = "elderpath-server.fly.dev";
    static final int DEFAULT_TLS_PORT = 443;
    static final int DEFAULT_LOCAL_PORT = 4567;

    /** @throws IllegalArgumentException with a message fit to show the player */
    public static LobbyEndpoint parse(String text) {
        String address = text == null ? "" : text.strip();
        Boolean forcedTls = null;
        if (address.regionMatches(true, 0, "tls://", 0, 6)) {
            forcedTls = true;
            address = address.substring(6);
        } else if (address.regionMatches(true, 0, "tcp://", 0, 6)) {
            forcedTls = false;
            address = address.substring(6);
        }
        if (address.isEmpty()) throw new IllegalArgumentException("Enter a server address");

        String host = address;
        Integer port = null;
        int colon = address.lastIndexOf(':');
        if (colon >= 0) {
            host = address.substring(0, colon);
            String portText = address.substring(colon + 1);
            try {
                int value = Integer.parseInt(portText);
                if (value < 1 || value > 65535) throw new NumberFormatException();
                port = value;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("The port must be a number from 1 to 65535");
            }
        }
        if (host.isEmpty() || !host.matches("[A-Za-z0-9.-]+")) {
            throw new IllegalArgumentException("That is not a valid server address");
        }
        boolean local = host.equalsIgnoreCase("localhost") || host.startsWith("127.");
        boolean tls = forcedTls != null ? forcedTls : !local;
        int resolvedPort = port != null ? port : (tls ? DEFAULT_TLS_PORT : DEFAULT_LOCAL_PORT);
        return new LobbyEndpoint(host, resolvedPort, tls);
    }
}
