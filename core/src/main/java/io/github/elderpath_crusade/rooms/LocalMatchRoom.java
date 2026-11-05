package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.supers.Room;

/**
 * Hotseat: one client process controls both seats on the same match server. The server has no
 * notion of "pass the device" — turns just proceed server-side as soon as a command arrives —
 * so the handoff gate (hide the next player's hand until they click through) is purely this
 * room's own client-side state.
 */
public class LocalMatchRoom extends BattleRoom {
    private boolean revealed = true;

    private LocalMatchRoom(ClientMatchSession session) {
        super(GameMode.LOCAL_MATCH, session);
        layoutBoard();

        // Initially show current player's hand
        hideAllHands();
        PieceAlignment current = session.currentPlayer();
        if (current != null) showPlayerHand(current);
    }

    @Override
    protected void onPassTurnClicked() {
        if (!revealed) {
            revealed = true;
            PieceAlignment current = session.currentPlayer();
            if (current != null) showPlayerHand(current);
            passTurn.setLabel("Pass Turn");
            return;
        }
        controller.endTurn();
    }

    /** Hotseat: the screen is oriented for whoever's turn it is. */
    @Override
    protected PieceAlignment viewerAlignment() {
        PieceAlignment current = session.currentPlayer();
        return current != null ? current : PieceAlignment.P1;
    }

    /** A new player is up — hide both hands and ask them to click through before seeing it. */
    @Override
    protected void onTurnChanged(PieceAlignment newPlayer) {
        revealed = false;
        hideAllHands();
        passTurn.setLabel("Start Turn");
    }

    private void hideAllHands() {
        for (Card card : handP1.getCards()) card.showBack();
        for (Card card : handP2.getCards()) card.showBack();
    }

    private void showPlayerHand(PieceAlignment player) {
        Hand hand = (player == PieceAlignment.P1) ? handP1 : handP2;
        if (hand != null) {
            for (Card card : hand.getCards()) card.showFront();
        }
    }

    public static Room get() {
        return MatchStartingRoom.get(LocalMatchRoom::startSession, LocalMatchRoom::new);
    }

    private static ClientMatchSession startSession() {
        LocalMatchSpec spec = LocalMatchSpec.twoHumans(
                GameContext.get().getDeckManager().getDraftedDeck(PieceAlignment.P1),
                GameContext.get().getDeckManager().getDraftedDeck(PieceAlignment.P2));
        return ClientMatchSession.startLocal(spec);
    }
}
