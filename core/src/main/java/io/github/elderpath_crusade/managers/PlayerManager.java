package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.GameContext;
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
 * Instance held by GameContext; access via GameContext.get().getPlayerManager().
 */
public class PlayerManager {
    public static class PlayerState {
        public final PieceAlignment id;
        public int mana = 0;
        public Hand hand;
        public Deck deck;
        public PlayerState(PieceAlignment id) { this.id = id; }
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
        p1.mana = 0;
        p2.mana = 0;
        p1.hand = null;
        p1.deck = null;
        p2.hand = null;
        p2.deck = null;
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
        ps.mana += 1;
        TypedEventBus.get().emit(new ManaChangedEvent(id, ps.mana));
        draw(ps, 3);
        applyBotHandVisibilityOnTurnStart(id);
        resetActionsFor(id);
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

    private void resetActionsFor(PieceAlignment id) {
        Board b = BoardManager.getBoard();
        if (b != null) {
            b.resetActionsForOwner(id);
        }
    }

    private void applyBotHandVisibilityOnTurnStart(PieceAlignment current) {
        if (GameModeManager.getCurrent() == GameMode.LOCAL_MATCH) return;
        if (!SettingsManager.debug.enableP2Bot) return;
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
