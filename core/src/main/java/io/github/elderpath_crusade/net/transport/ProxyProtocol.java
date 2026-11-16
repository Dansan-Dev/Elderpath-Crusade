package io.github.elderpath_crusade.net.transport;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

/**
 * PROXY protocol version 1: a hosting platform's TCP proxy (fly.io's {@code proxy_proto} handler)
 * can put one text line in front of each connection naming the client's real address, which the
 * server would otherwise never see (it only sees the proxy). Read it byte by byte so nothing
 * beyond the line is consumed.
 *
 * Only enable it behind such a proxy: anyone who can reach the server directly could forge the
 * line and pick which address the per-address limits see.
 */
public final class ProxyProtocol {
    /** The longest legal v1 header, per the specification. */
    static final int MAX_HEADER_CHARS = 107;

    private ProxyProtocol() {}

    /**
     * The client address in a header line such as {@code PROXY TCP4 203.0.113.7 10.0.0.1 5555 443}
     * (without its CRLF), or null for {@code PROXY UNKNOWN}.
     *
     * @throws IOException if the line is not a valid v1 header
     */
    public static String parse(String line) throws IOException {
        String[] parts = line.split(" ");
        if (parts.length < 2 || !parts[0].equals("PROXY")) throw new IOException("not a PROXY protocol header");
        if (parts[1].equals("UNKNOWN")) return null;
        if (!(parts[1].equals("TCP4") || parts[1].equals("TCP6")) || parts.length != 6) {
            throw new IOException("malformed PROXY protocol header");
        }
        String address = parts[2];
        if (!looksLikeAddress(address)) throw new IOException("malformed client address in PROXY header");
        // Normalise (a IPv6 address has many spellings) so one client is one key.
        try {
            return InetAddress.getByName(address).getHostAddress();
        } catch (java.net.UnknownHostException e) {
            throw new IOException("malformed client address in PROXY header", e);
        }
    }

    /** Only digits, hex letters, dots and colons: never a host name, so no DNS lookup is ever triggered. */
    private static boolean looksLikeAddress(String s) {
        if (s.isEmpty() || s.length() > 45) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.digit(c, 16) >= 0 || c == '.' || c == ':')) return false;
        }
        return true;
    }

    /** Reads one header line (up to its LF) from in and returns the client address, or null for UNKNOWN. */
    public static String read(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        while (true) {
            int b = in.read();
            if (b < 0) throw new IOException("connection closed before the PROXY header");
            if (b == '\n') break;
            if (line.length() >= MAX_HEADER_CHARS) throw new IOException("PROXY header too long");
            line.append((char) b);
        }
        int length = line.length();
        if (length > 0 && line.charAt(length - 1) == '\r') line.setLength(length - 1);
        return parse(line.toString());
    }
}
