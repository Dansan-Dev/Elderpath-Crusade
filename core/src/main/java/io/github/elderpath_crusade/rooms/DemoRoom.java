package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.abilities.AbilityRelay;
import io.github.elderpath_crusade.cards.*;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.Deck;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.managers.DeckManager;
import io.github.elderpath_crusade.managers.InteractionManager;
import io.github.elderpath_crusade.managers.PlayerManager;
import io.github.elderpath_crusade.managers.MusicManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.ui_objects.*;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.managers.GameModeManager;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.Logger;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DemoRoom extends BattleRoom {

    private DemoRoom() {
        super(GameMode.DEMO);

        // Finalize layout
        layoutBoard();
    }

    @Override
    protected void onPassTurnClicked() {
        TurnManager.endTurn();
    }

    @Override
    protected Deck createDeck(PieceAlignment alignment, Hand hand) {
        List<Card> cards = new ArrayList<>();
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();

        if (alignment == PieceAlignment.P1) {
            // P1 Deck Logic
            if (DeckManager.hasDraftedDeck()) {
                List<java.util.function.Function<DeckManager.CardCreationParams, SummonCard>> draftedDeck = DeckManager.getDraftedDeck();
                for (java.util.function.Function<DeckManager.CardCreationParams, SummonCard> cardCreator : draftedDeck) {
                    DeckManager.CardCreationParams params = new DeckManager.CardCreationParams(
                        board, PieceAlignment.P1, 0, 0, 125, 200, 0
                    );
                    cards.add(cardCreator.apply(params));
                }
            } else {
                for (int i = 0; i < 24; i++) {
                    int kind = i % 12;
                    if (kind == 0) cards.add(new WolfCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    else if (kind == 1) cards.add(new RogueCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    else if (kind == 2) cards.add(new FairyCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    else if (kind == 3) cards.add(new WindSpiritCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    else if (kind == 4) cards.add(new BigToadCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    else if (kind == 5) cards.add(new SniperCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    else if (kind == 6) cards.add(new BarbarianCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    else if (kind == 7) cards.add(new KingCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    else if (kind == 8) cards.add(new ChargerCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    else if (kind == 9) cards.add(new CrossbowmanCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    else if (kind == 10) cards.add(new SkeletonBomberCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                    else cards.add(new WarpMageCard(board, PieceAlignment.P1, 0, 0, 125, 200, 0));
                }
            }
            Deck deck = new Deck(cards, 0, 10, 125, 200, 1, SpriteBoxPos.BOTTOM_LEFT);
            deck.shuffle();
            deck.getBounds().setX(screenW - deck.getWidth() - 10);
            deck.setOwner(PieceAlignment.P1);
            deck.setHand(hand);
            return deck;
        } else {
            // P2 Deck Logic
            for (int i = 0; i < 10; i++) {
                if (i % 2 == 0) cards.add(new RiflemanCard(board, PieceAlignment.P2, 0, 0, 125, 200, 0));
                else cards.add(new RogueCard(board, PieceAlignment.P2, 0, 0, 125, 200, 0));
            }
            Deck deck = new Deck(cards, 0, screenH - 200, 125, 200, 1, SpriteBoxPos.BOTTOM_LEFT);
            deck.shuffle();
            deck.getBounds().setX(screenW - deck.getWidth() - 10);
            deck.getBounds().setY(screenH - deck.getHeight() - 10);
            deck.setOwner(PieceAlignment.P2);
            deck.setHand(hand);
            return deck;
        }
    }

    public static DemoRoom get() {
        return new DemoRoom();
    }
}
