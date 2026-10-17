package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.bot.BotManager;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.cards.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DemoRoom extends BattleRoom {

    private DemoRoom() {
        super(GameMode.DEMO);

        TypedEventBus.get().registerScoped("session", TurnStartedEvent.class, this::applyBotHandVisibility);

        // Finalize layout
        layoutBoard();
    }

    @Override
    protected void onPassTurnClicked() {
        GameContext.get().getTurnManager().endTurn();
    }

    @Override
    protected List<String> deckCardNames(PieceAlignment alignment) {
        List<String> names = new ArrayList<>();
        if (alignment == PieceAlignment.P1) {
            if (GameContext.get().getDeckManager().hasDraftedDeck(PieceAlignment.P1)) {
                names.addAll(GameContext.get().getDeckManager().getDraftedDeck(PieceAlignment.P1));
            } else {
                String[] fallbackDeck = {"Rogue","Fairy","Wind Spirit","Big Toad","Sniper",
                        "Barbarian","King","Charger","Crossbowman","Skeleton Bomber","Warp Mage"};
                for (int i = 0; i < 24; i++) names.add(fallbackDeck[i % fallbackDeck.length]);
            }
        } else {
            for (int i = 0; i < 10; i++) names.add((i % 2 == 0) ? "Rifleman" : "Rogue");
        }
        return names;
    }

    /** P2 is a bot unless the debug setting turns it off. */
    @Override
    protected Map<PieceAlignment, BotKind> botSlots() {
        return GameContext.get().getSettingsManager().debug.enableP2Bot
                ? Map.of(PieceAlignment.P2, BotKind.SMART)
                : Map.of();
    }

    /** With the P2 bot enabled, its hand is face-down during its own turn and face-up during P1's. */
    private void applyBotHandVisibility(TurnStartedEvent event) {
        if (!BotManager.isBotControlled(PieceAlignment.P2)) return;
        boolean faceUp = event.player() == PieceAlignment.P1;
        for (Card c : handP2.getCards()) {
            if (c != null && c.isFaceUp() != faceUp) {
                if (faceUp) c.showFront(); else c.showBack();
            }
        }
    }

    public static DemoRoom get() {
        return new DemoRoom();
    }
}
