package io.github.elderpath_crusade.run;

/**
 * Non-battle relic effects (UPG-002) — hook points {@code RunManager} calls at specific
 * moments between battles, since those have no board/server concern (a roguelike run is
 * single-player; {@code RunManager} already is the authority for everything between battles).
 * Deliberately open-ended (every method defaults to a no-op) so more hook points can be added
 * later without touching an existing relic — one implements only the hooks it cares about.
 *
 * <p>The plan's original phrasing for this ticket included a "map generation" hook, modeled
 * after reacting to a fresh run's map coming into existence. In practice a relic can only ever
 * be active once it's been picked up as a battle reward — after the map it would react to
 * already exists — so that moment never actually occurs for any relic. {@link #onAcquired} is
 * the honest substitute: it fires right when a relic enters the run, which is the earliest
 * moment any relic's non-battle effect can ever matter.
 */
public interface RelicRunHook {
    /** Fires once, right when this relic is added to the run ({@code RunManager.addRelic}). */
    default void onAcquired(RunState state) {
    }

    /** Fires whenever the player's current node becomes a Rest node. */
    default void onEnterRestNode(RunState state) {
    }
}
