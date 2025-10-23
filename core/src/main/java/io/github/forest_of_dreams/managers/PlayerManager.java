package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.cards.Card;
import io.github.forest_of_dreams.game_objects.cards.Deck;
import io.github.forest_of_dreams.game_objects.cards.Hand;
import io.github.forest_of_dreams.interfaces.Renderable;

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

    public static PlayerState get(PieceAlignment id) { return id == PieceAlignment.P1 ? p1 : p2; }

    public static PlayerState getCurrent() { return get(TurnManager.getCurrentPlayer()); }

    // Wiring helpers
    public static void setHand(PieceAlignment id, Hand hand) { get(id).hand = hand; }
    public static void setDeck(PieceAlignment id, Deck deck) { get(id).deck = deck; }

    // Turn hooks (called by TurnManager)
    public static void onStartTurn(PieceAlignment id) {
        PlayerState ps = get(id);
        // +1 mana
        ps.mana += 1;
        // Draw 3
        draw(ps, 3);
        // Reset actions for that player's pieces on all boards currently rendered
        resetActionsFor(id);
    }

    public static void onEndTurn(PieceAlignment id) {
        PlayerState ps = get(id);
        // Discard hand (all cards)
        discardHand(ps);
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
        for (Renderable r : GraphicsManager.getRenderables()) {
            if (r instanceof Board b) {
                b.resetActionsForOwner(id);
            }
        }
    }
}
