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
            GameContext.get().getTurnManager().endTurn();
        }
    }

    @Override
    protected List<String> deckCardNames(PieceAlignment alignment) {
        return GameContext.get().getDeckManager().getDraftedDeck(alignment);
    }

    private void onTurnStarted(TurnStartedEvent event) {
        PieceAlignment player = event.player();
        if (passTurn != null) passTurn.updateButtonText();
        swapHandPositionsIfNeeded(player);
        showPlayerHand(player);
    }

    private void onTurnEnded(TurnEndedEvent event) {
        if (passTurn != null) passTurn.updateButtonText();
        hideAllHands();
    }

    private void swapHandPositionsIfNeeded(PieceAlignment currentPlayer) {
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        int screenCenterX = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0];
        int p1HandBottomY = -80; // from BattleRoom init

        if (currentPlayer == PieceAlignment.P2) {
            handP1.setBottomY(screenH - handP1.getHeight());
            handP2.setBottomY(p1HandBottomY);
        } else {
            handP1.setBottomY(p1HandBottomY);
            handP2.setBottomY(screenH - handP2.getHeight());
        }
        handP1.setCenterX(screenCenterX);
        handP1.updateBounds();
        handP2.setCenterX(screenCenterX);
        handP2.updateBounds();
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

    @Override
    public void onScreenResize() {
        super.onScreenResize();
        swapHandPositionsIfNeeded(GameContext.get().getTurnManager().getCurrentPlayer());
    }

    public static LocalMatchRoom get() {
        return new LocalMatchRoom();
    }
}
