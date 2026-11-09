package io.github.elderpath_crusade.run;

import java.util.UUID;

/**
 * Two concrete non-battle relic hooks (UPG-002), proving {@link RelicRunHook}'s shape end to
 * end: one fires the instant the relic is picked up, the other every time the run rests. Both
 * reuse {@code RunState.addCardToDeck} — no new run-state mechanism needed for either.
 * Registering one of these under a relic id ({@code RelicRegistry.register}) is how a run
 * actually offers it; this class only holds the behavior.
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

    /** Grants one copy of rewardCardName to the deck every time the run enters a Rest node. */
    public static RelicRunHook grantCardOnRest(String rewardCardName) {
        return new RelicRunHook() {
            @Override
            public void onEnterRestNode(RunState state) {
                state.addCardToDeck(RunCardInstance.unmodified(newInstanceId(), rewardCardName));
            }
        };
    }

    private static String newInstanceId() {
        return "relic-reward-" + UUID.randomUUID();
    }
}
