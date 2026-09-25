package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.systems.PlayerSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.events.ActionsResetEvent;
import io.github.elderpath_crusade.events.CardDiscardedEvent;
import io.github.elderpath_crusade.events.ManaChangedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.Deck;
import io.github.elderpath_crusade.game_objects.cards.Hand;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns PlayerState for P1 and P2 and performs per-turn start/end actions
 * when invoked by TurnManager. Kept minimal and self-contained.
 *
 * Mana is ECS-backed (PlayerComponent via PlayerSystem) — this class is a facade
 * over it, mirroring how TurnManager facades TurnSystem.
 *
 * Instance held by GameContext; access via GameContext.get().getPlayerManager().
 */
public class PlayerManager {
    public static class PlayerState {
        public final PieceAlignment id;
        public Hand hand;
        public Deck deck;
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
        p1.hand = null;
        p1.deck = null;
        p2.hand = null;
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

    public void setHand(PieceAlignment id, Hand hand) { get(id).hand = hand; }
    public void setDeck(PieceAlignment id, Deck deck) { get(id).deck = deck; }

    public void onStartTurn(PieceAlignment id) {
        PlayerState ps = get(id);
        ps.addMana(1);
        TypedEventBus.get().emit(new ManaChangedEvent(id, ps.getMana()));
        draw(ps, 3);
        applyBotHandVisibilityOnTurnStart(id);
        // Action reset handled by TurnSystem via ECS
        TypedEventBus.get().emit(new ActionsResetEvent(id));
    }

    public void onEndTurn(PieceAlignment id) {
        PlayerState ps = get(id);
        int discarded = (ps.hand == null ? 0 : ps.hand.getCards().size());
        discardHand(ps);
        TypedEventBus.get().emit(new CardDiscardedEvent(id, discarded));
    }

    private void draw(PlayerState ps, int n) {
        if (ps.deck == null || ps.hand == null) return;
        for (int i = 0; i < n; i++) ps.deck.draw();
    }

    private void discardHand(PlayerState ps) {
        if (ps.hand == null || ps.deck == null) return;
        List<Card> snapshot = new ArrayList<>(ps.hand.getCards());
        for (Card c : snapshot) {
            c.consume();
        }
        ps.hand.updateBounds();
    }

    private void applyBotHandVisibilityOnTurnStart(PieceAlignment current) {
        if (GameContext.get().getGameModeManager().getCurrent() == GameMode.LOCAL_MATCH) return;
        if (!GameContext.get().getSettingsManager().debug.enableP2Bot) return;
        PlayerState bot = get(PieceAlignment.P2);
        if (bot.hand == null) return;
        if (current == PieceAlignment.P2) {
            for (Card c : bot.hand.getCards()) {
                if (c != null && c.isFaceUp()) c.showBack();
            }
        } else if (current == PieceAlignment.P1) {
            for (Card c : bot.hand.getCards()) {
                if (c != null && !c.isFaceUp()) c.showFront();
            }
        }
    }
}
