package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.game.TurnManager;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.config.SettingsManager;

import java.util.List;

public class LocalMatchRoom extends BattleRoom {

    private LocalMatchRoom() {
        super(GameMode.LOCAL_MATCH);

        // LocalMatch-specific event listeners
        TypedEventBus.get().registerScoped("session", TurnStartedEvent.class, this::onTurnStarted);
        TypedEventBus.get().registerScoped("session", TurnEndedEvent.class, this::onTurnEnded);

        layoutBoard();

        // Initially show current player's hand
        hideAllHands();
        showPlayerHand(GameContext.get().getTurnManager().getCurrentPlayer());
    }

    @Override
    protected void onPassTurnClicked() {
        if (GameContext.get().getTurnManager().isWaitingForNextPlayer()) {
            GameContext.get().getTurnManager().startNextPlayerTurn();
        } else {
            GameContext.get().getGameServer().endTurn(GameContext.get().getLocalControl().actingSide());
        }
    }

    /** Hotseat: the screen is oriented for whoever's turn it is. */
    @Override
    protected PieceAlignment viewerAlignment() {
        return GameContext.get().getTurnManager().getCurrentPlayer();
    }

    @Override
    protected List<String> deckCardNames(PieceAlignment alignment) {
        return GameContext.get().getDeckManager().getDraftedDeck(alignment);
    }

    private void onTurnStarted(TurnStartedEvent event) {
        PieceAlignment player = event.player();
        if (passTurn != null) passTurn.updateButtonText();
        showPlayerHand(player);
    }

    private void onTurnEnded(TurnEndedEvent event) {
        if (passTurn != null) passTurn.updateButtonText();
        hideAllHands();
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

    public static LocalMatchRoom get() {
        return new LocalMatchRoom();
    }
}
