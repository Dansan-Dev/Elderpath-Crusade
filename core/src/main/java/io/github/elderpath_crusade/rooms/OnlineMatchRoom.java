package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.game.DeckManager;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.CardFactory;
import io.github.elderpath_crusade.game_objects.cards.Deck;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import io.github.elderpath_crusade.server.ActionDispatcher;
import io.github.elderpath_crusade.server.ReplicaEventApplier;

import java.util.ArrayList;
import java.util.List;

/**
 * A tactical battle over the network. The host runs the real simulation exactly like local
 * play (see GameServer) — its own alignment is always P1. The guest is a thin client: its
 * clicks route to the host via ActionDispatcher/NetworkCommand, and its board/hand state is
 * mirrored from the host's relayed events via ReplicaEventApplier — its own alignment is
 * always P2. See ReplicaEventApplier's class doc for what is and isn't reconstructed.
 *
 * Skips the draft flow for this prototype — both sides start with the same fixed deck
 * (2x Wolf + 2x Wolf Cub, matching DraftRoom's starting deck).
 */
public class OnlineMatchRoom extends BattleRoom {
    private static final int STARTING_WOLVES = 2;
    private static final int STARTING_WOLF_CUBS = 2;

    private OnlineMatchRoom() {
        super(GameMode.ONLINE_MATCH);

        if (GameContext.get().getOnlineMatch().isGuest()) {
            ReplicaEventApplier applier = new ReplicaEventApplier();
            GameContext.get().getOnlineMatch().getClient().addListener(applier::apply);
        }

        layoutBoard();
        applyFixedHandVisibility();
    }

    /**
     * Unlike hotseat (LocalMatchRoom), each screen has exactly one local human — so hand
     * visibility doesn't need to track whose turn it is, only whose screen this is: my own
     * hand is always visible, the opponent's is always hidden. Newly-drawn cards on the
     * guest's own hand are shown via ReplicaEventApplier.onCardDrawn.
     */
    private void applyFixedHandVisibility() {
        PieceAlignment local = GameContext.get().getOnlineMatch().getLocalAlignment();
        for (Card c : handP1.getCards()) {
            if (local == PieceAlignment.P1) c.showFront(); else c.showBack();
        }
        for (Card c : handP2.getCards()) {
            if (local == PieceAlignment.P2) c.showFront(); else c.showBack();
        }
    }

    @Override
    protected void onPassTurnClicked() {
        PieceAlignment local = GameContext.get().getOnlineMatch().getLocalAlignment();
        if (local == null) return;
        ActionDispatcher.dispatch(
                () -> new NetworkCommand.EndTurn(local),
                () -> {
                    GameContext.get().getTurnManager().endTurn();
                    return true;
                }
        );
    }

    @Override
    protected Deck createDeck(PieceAlignment alignment, Hand hand) {
        List<Card> cards = new ArrayList<>();
        // Only the host's decks are real — the guest never draws locally; its own hand is
        // reconstructed from CardDrawnEvent (see ReplicaEventApplier), and the opponent's
        // hand isn't rendered on the guest's screen at all.
        if (GameContext.get().getOnlineMatch().isHost()) {
            for (int i = 0; i < STARTING_WOLVES; i++) {
                cards.add(CardFactory.create("Wolf", new DeckManager.CardCreationParams(board, alignment, 0, 0, 125, 200, 0)));
            }
            for (int i = 0; i < STARTING_WOLF_CUBS; i++) {
                cards.add(CardFactory.create("Wolf Cub", new DeckManager.CardCreationParams(board, alignment, 0, 0, 125, 200, 0)));
            }
        }

        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        Deck deck = new Deck(cards, 0, 0, 125, 200, 1, SpriteBoxPos.BOTTOM_LEFT);
        deck.shuffle();
        deck.getBounds().setX(screenW - deck.getWidth() - 10);
        deck.getBounds().setY(alignment == PieceAlignment.P1 ? 10 : screenH - deck.getHeight() - 10);
        deck.setOwner(alignment);
        deck.setHand(hand);
        return deck;
    }

    public static OnlineMatchRoom get() {
        return new OnlineMatchRoom();
    }
}
