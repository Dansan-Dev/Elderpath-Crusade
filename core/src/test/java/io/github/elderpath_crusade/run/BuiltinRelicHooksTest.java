package io.github.elderpath_crusade.run;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinRelicHooksTest {

    private static RunState freshState() {
        return RunState.start(RunMap.linearChain(2), RunDifficulty.DEFAULT, List.of("Wolf"), 1);
    }

    @Test
    void grantCardOnAcquire_addsACardWhenFired() {
        RunState state = freshState();
        RelicRunHook hook = BuiltinRelicHooks.grantCardOnAcquire("Wolf Cub");

        hook.onAcquired(state);

        assertEquals(2, state.deck().size());
        assertEquals("Wolf Cub", state.deck().get(1).cardName());
    }

    @Test
    void repeatedFirings_eachGetAUniqueInstanceId() {
        RunState state = freshState();
        RelicRunHook hook = BuiltinRelicHooks.grantCardOnAcquire("Wolf Cub");

        // addCardToDeck throws on a duplicate instance id — reaching here twice proves uniqueness
        assertDoesNotThrow(() -> {
            hook.onAcquired(state);
            hook.onAcquired(state);
        });
        assertEquals(3, state.deck().size());
    }
}
