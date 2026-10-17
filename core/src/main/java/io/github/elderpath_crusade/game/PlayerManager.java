package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.systems.PlayerSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.ActionsResetEvent;
import io.github.elderpath_crusade.events.CardDiscardedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.DeckState;
import io.github.elderpath_crusade.match.HandState;

import java.util.List;
import java.util.Random;

/**
 * Owns PlayerState for P1 and P2 and performs per-turn start/end actions
 * when invoked by TurnManager. Kept minimal and self-contained.
 *
 * Mana is ECS-backed (PlayerComponent via PlayerSystem) — this class is a facade
 * over it, mirroring how TurnManager facades TurnSystem. Hands and decks are pure state
 * (HandState/DeckState); renderable hands are views kept in step via the card events.
 *
 * Instance held by GameContext; access via GameContext.get().getPlayerManager().
 */
public class PlayerManager {
    public static class PlayerState {
        public final PieceAlignment id;
        public final HandState hand = new HandState();
        public DeckState deck;
        public PlayerState(PieceAlignment id) { this.id = id; }

        public int getMana() { return playerSystem().getMana(id); }
        public void setMana(int value) { playerSystem().setMana(id, value); }
        public void addMana(int delta) { playerSystem().addMana(id, delta); }

        private PlayerSystem playerSystem() {
            return GameContext.get().getEcsEngine().getSystem(PlayerSystem.class);
        }
    }

    private boolean initialized = false;
    private final PlayerState p1;
    private final PlayerState p2;

    public PlayerManager() {
        this.p1 = new PlayerState(PieceAlignment.P1);
        this.p2 = new PlayerState(PieceAlignment.P2);
    }

    public void initializeIfNeeded() {
        if (!initialized) initialized = true;
    }

    public void resetForNewGame() {
        p1.hand.clear();
        p1.deck = null;
        p2.hand.clear();
        p2.deck = null;
        GameContext.get().getEcsEngine().getSystem(PlayerSystem.class).resetForNewGame();
    }

    public PlayerState get(PieceAlignment id) {
        return id == PieceAlignment.P1 ? p1 : p2;
    }

    public PlayerState getCurrent() {
        return get(GameContext.get().getTurnManager().getCurrentPlayer());
    }

    public PieceAlignment getLocalPlayer() {
        return PieceAlignment.P1;
    }

    /** Gives a player a fresh (unshuffled) deck of these card names and an empty hand. */
    public DeckState setDeck(PieceAlignment id, List<String> cardNames, Random rng) {
        PlayerState ps = get(id);
        ps.hand.clear();
        ps.deck = new DeckState(id, ps.hand, cardNames, rng);
        return ps.deck;
    }

    public void onStartTurn(PieceAlignment id) {
        PlayerState ps = get(id);
        ps.addMana(1); // PlayerSystem.setMana emits ManaChangedEvent itself
        draw(ps, 3);
        // Action reset handled by TurnSystem via ECS
        TypedEventBus.get().emit(new ActionsResetEvent(id));
    }

    public void onEndTurn(PieceAlignment id) {
        PlayerState ps = get(id);
        if (ps.deck != null) {
            ps.deck.discardHand(); // emits CardDiscardedEvent
        } else {
            TypedEventBus.get().emit(new CardDiscardedEvent(id, 0, List.of()));
        }
    }

    private void draw(PlayerState ps, int n) {
        if (ps.deck == null) return;
        for (int i = 0; i < n; i++) ps.deck.draw();
    }
}
