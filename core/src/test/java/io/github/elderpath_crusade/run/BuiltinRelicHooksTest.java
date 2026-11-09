package io.github.elderpath_crusade.run;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinRelicHooksTest {

    private static RunState freshState() {
        return RunState.start(RunMap.linearChain(2), RunDifficulty.DEFAULT, List.of("Wolf"));
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
    void grantCardOnAcquire_doesNotFireOnEnterRestNode() {
        RunState state = freshState();
        RelicRunHook hook = BuiltinRelicHooks.grantCardOnAcquire("Wolf Cub");

        hook.onEnterRestNode(state); // the default no-op — this hook only reacts to onAcquired

        assertEquals(1, state.deck().size());
    }

    @Test
    void grantCardOnRest_addsACardWhenFired() {
        RunState state = freshState();
        RelicRunHook hook = BuiltinRelicHooks.grantCardOnRest("Wolf Cub");

        hook.onEnterRestNode(state);

        assertEquals(2, state.deck().size());
        assertEquals("Wolf Cub", state.deck().get(1).cardName());
    }

    @Test
    void grantCardOnRest_doesNotFireOnAcquired() {
        RunState state = freshState();
        RelicRunHook hook = BuiltinRelicHooks.grantCardOnRest("Wolf Cub");

        hook.onAcquired(state); // the default no-op — this hook only reacts to onEnterRestNode

        assertEquals(1, state.deck().size());
    }

    @Test
    void repeatedFirings_eachGetAUniqueInstanceId() {
        RunState state = freshState();
        RelicRunHook hook = BuiltinRelicHooks.grantCardOnRest("Wolf Cub");

        // addCardToDeck throws on a duplicate instance id — reaching here twice proves uniqueness
        assertDoesNotThrow(() -> {
            hook.onEnterRestNode(state);
            hook.onEnterRestNode(state);
        });
        assertEquals(3, state.deck().size());
    }
}
