package io.github.elderpath_crusade.run;

import java.util.UUID;

/**
 * A concrete non-battle relic hook (UPG-002), proving {@link RelicRunHook}'s shape: fires the
 * instant the relic is picked up, reusing {@code RunState.addCardToDeck} — no new run-state
 * mechanism needed. Registering one of these under a relic id ({@code RelicRegistry.register})
 * is how a run actually offers it; this class only holds the behavior.
 *
 * <p>Used to also have a {@code grantCardOnRest} hook, firing on entering a Rest node — removed
 * along with Rest nodes themselves (2026-11-12, user's call): no relic ever defined in the real
 * game data actually used it (a yaml-authored relic is always {@link RelicDefinition#battleOnly}),
 * and the node type it reacted to no longer exists.
 */
public final class BuiltinRelicHooks {
    private BuiltinRelicHooks() {
    }

    /** Grants one copy of rewardCardName to the deck the instant this relic is picked up. */
    public static RelicRunHook grantCardOnAcquire(String rewardCardName) {
        return new RelicRunHook() {
            @Override
            public void onAcquired(RunState state) {
                state.addCardToDeck(RunCardInstance.unmodified(newInstanceId(), rewardCardName));
            }
        };
    }

    private static String newInstanceId() {
        return "relic-reward-" + UUID.randomUUID();
    }
}
