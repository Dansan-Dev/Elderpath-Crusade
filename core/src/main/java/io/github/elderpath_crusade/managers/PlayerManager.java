package io.github.elderpath_crusade.managers;

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
import io.github.elderpath_crusade.interfaces.Renderable;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns PlayerState for P1 and P2 and performs per-turn start/end actions
 * when invoked by TurnManager. Kept minimal and self-contained.
 */
public class PlayerManager {
    public static class PlayerState {
        public final PieceAlignment id; // P1 or P2
        public int mana = 0;
        public Hand hand; // wired in DemoRoom (Chunk C)
        public Deck deck; // wired in DemoRoom (Chunk C)
        public PlayerState(PieceAlignment id) { this.id = id; }
    }

    private static boolean initialized = false;
    private static final PlayerState p1 = new PlayerState(PieceAlignment.P1);
    private static final PlayerState p2 = new PlayerState(PieceAlignment.P2);

    public static void initializeIfNeeded() {
        if (!initialized) initialized = true;
    }

    /** Reset player state for a brand new game/session. */
    public static void resetForNewGame() {
        // Reset mana
        p1.mana = 0;
        p2.mana = 0;
        // Clear wiring to any previous Hands/Decks; new room will rewire
        p1.hand = null;
        p1.deck = null;
        p2.hand = null;
        p2.deck = null;
    }

    public static PlayerState get(PieceAlignment id) { return id == PieceAlignment.P1 ? p1 : p2; }

    public static PlayerState getCurrent() { return get(TurnManager.getCurrentPlayer()); }

    /**
     * Returns the local human player's alignment. For now the local player is P1.
     * This centralizes perspective decisions for UI like Victory/Loss screens.
     */
    public static PieceAlignment getLocalPlayer() { return PieceAlignment.P1; }

    // Wiring helpers
    public static void setHand(PieceAlignment id, Hand hand) { get(id).hand = hand; }
    public static void setDeck(PieceAlignment id, Deck deck) { get(id).deck = deck; }

    // Turn hooks (called by TurnManager)
    public static void onStartTurn(PieceAlignment id) {
        PlayerState ps = get(id);
        // +1 mana
        ps.mana += 1;
        // Emit mana changed
        TypedEventBus.get().emit(new ManaChangedEvent(id, ps.mana));
        // Draw 3
        draw(ps, 3);
        // Ensure bot hand visibility rules (flip P2 hand to back during bot's turn)
        applyBotHandVisibilityOnTurnStart(id);
        // Reset actions for that player's pieces on all boards currently rendered
        resetActionsFor(id);
        TypedEventBus.get().emit(new ActionsResetEvent(id));
    }

    public static void onEndTurn(PieceAlignment id) {
        PlayerState ps = get(id);
        // Discard hand (all cards)
        int discarded = (ps.hand == null ? 0 : ps.hand.getCards().size());
        discardHand(ps);
        TypedEventBus.get().emit(new CardDiscardedEvent(id, discarded));
    }

    private static void draw(PlayerState ps, int n) {
        if (ps.deck == null || ps.hand == null) return;
        for (int i = 0; i < n; i++) ps.deck.draw();
    }

    private static void discardHand(PlayerState ps) {
        if (ps.hand == null || ps.deck == null) return;
        // Copy to avoid concurrent modification
        List<Card> snapshot = new ArrayList<>(ps.hand.getCards());
        for (Card c : snapshot) {
            // Reuse consume hook installed by Deck.addNewCards(); ensures removal from hand + add to discard
            c.consume();
        }
        ps.hand.updateBounds();
    }

    private static void resetActionsFor(PieceAlignment id) {
        Board b = BoardManager.getBoard();
        if (b != null) {
            b.resetActionsForOwner(id);
        }
    }

    // --- Bot hand visibility helpers ---
    private static void applyBotHandVisibilityOnTurnStart(PieceAlignment current) {
        // Skip in LOCAL_MATCH mode (both players are human, visibility handled by LocalMatchRoom)
        if (GameModeManager.getCurrent() == GameMode.LOCAL_MATCH) return;
        // Only applies when P2 is bot-controlled; feature flag lives in SettingsManager.debug.enableP2Bot
        if (!SettingsManager.debug.enableP2Bot) return;
        // When P2's turn starts, flip P2's hand cards face-down; when P1's turn starts, restore face-up.
        PlayerState bot = get(PieceAlignment.P2);
        if (bot.hand == null) return;
        if (current == PieceAlignment.P2) {
            // Flip any face-up cards to back; leave already face-down cards untouched to respect persistence
            for (Card c : bot.hand.getCards()) {
                if (c != null && c.isFaceUp()) c.showBack();
            }
        } else if (current == PieceAlignment.P1) {
            // Restore visibility for demo purposes: show P2 hand fronts when it's not the bot's turn
            for (Card c : bot.hand.getCards()) {
                if (c != null && !c.isFaceUp()) c.showFront();
            }
        }
    }
}
