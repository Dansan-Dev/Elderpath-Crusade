package io.github.elderpath_crusade.e2e;

import io.github.elderpath_crusade.client.AutoPlayer;

import java.util.Random;

/**
 * A "remote bot": plays a side through the client path only — it reads its ClientMatchState
 * and sends commands, exactly like a human's clicks would. Distinct from a server-side bot
 * seat, which plays inside the simulation. Now a thin wrapper over the production
 * {@link AutoPlayer} (ARCH-032), greedy by default so existing e2e runs stay deterministic.
 */
public final class RemoteBotDriver implements ClientDriver {
    private final AutoPlayer player;

    public RemoteBotDriver() {
        this(new AutoPlayer(AutoPlayer.Strategy.GREEDY, new Random(0)));
    }

    public RemoteBotDriver(AutoPlayer player) {
        this.player = player;
    }

    @Override
    public boolean act(TestClient testClient) {
        return player.step(testClient.client());
    }
}
