package io.github.elderpath_crusade.e2e;

import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.client.MatchClient;
import io.github.elderpath_crusade.net.protocol.ServerMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One game client in an end-to-end match: a MatchClient (the same replica + command code the
 * game will use), every raw line it received, and optionally a driver playing its side.
 */
public final class TestClient {
    private final String name;
    private final MatchClient client;
    // Written by a transport's reader thread (TCP), read by the harness thread.
    private final List<String> raw;
    private final ClientDriver driver;
    private ServerMessage.Rejected lastSeenRejection;
    private int leakScanned;

    TestClient(String name, MatchClient client, List<String> raw, ClientDriver driver) {
        this.name = name;
        this.client = client;
        this.raw = raw;
        this.driver = driver;
    }

    static List<String> newRawLog() {
        return new CopyOnWriteArrayList<>();
    }

    public String getName() {
        return name;
    }

    public MatchClient client() {
        return client;
    }

    public ClientMatchState state() {
        return client.getState();
    }

    public List<String> rawLines() {
        return raw;
    }

    ClientDriver driver() {
        return driver;
    }

    /** Lines received since the last call — for the incremental hidden-information scan. */
    List<String> unscannedLines() {
        List<String> out = raw.subList(leakScanned, raw.size());
        List<String> copy = List.copyOf(out);
        leakScanned += copy.size();
        return copy;
    }

    /** A rejection that arrived since the last call, or null. */
    ServerMessage.Rejected newRejection() {
        ServerMessage.Rejected latest = state().getLastRejection();
        if (latest == null || latest == lastSeenRejection) return null;
        lastSeenRejection = latest;
        return latest;
    }

    @Override
    public String toString() {
        return name + "(" + state().getSeat() + ")";
    }
}
