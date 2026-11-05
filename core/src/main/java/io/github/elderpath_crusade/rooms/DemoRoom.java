package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.supers.Room;

import java.util.ArrayList;
import java.util.List;

public class DemoRoom extends BattleRoom {

    private DemoRoom(ClientMatchSession session) {
        super(GameMode.DEMO, session);
        layoutBoard();
    }

    @Override
    protected void onPassTurnClicked() {
        controller.endTurn();
    }

    /** With the P2 bot enabled, its hand is face-down during its own turn and face-up during P1's. */
    @Override
    protected void onTurnChanged(PieceAlignment newPlayer) {
        String p2Slot = session.viewState() != null ? session.viewState().getSlots().get(PieceAlignment.P2) : null;
        if (p2Slot == null || !p2Slot.startsWith("BOT_")) return;
        boolean faceUp = newPlayer == PieceAlignment.P1;
        for (Card c : handP2.getCards()) {
            if (c != null && c.isFaceUp() != faceUp) {
                if (faceUp) c.showFront(); else c.showBack();
            }
        }
    }

    public static Room get() {
        return MatchStartingRoom.get(DemoRoom::startSession, DemoRoom::new);
    }

    private static ClientMatchSession startSession() {
        boolean botEnabled = GameContext.get().getSettingsManager().debug.enableP2Bot;
        LocalMatchSpec spec = botEnabled
                ? LocalMatchSpec.solo(p1DeckNames(), p2DeckNames(), BotKind.SMART)
                : LocalMatchSpec.twoHumans(p1DeckNames(), p2DeckNames());
        return ClientMatchSession.startLocal(spec);
    }

    private static List<String> p1DeckNames() {
        List<String> names = new ArrayList<>();
        if (GameContext.get().getDeckManager().hasDraftedDeck(PieceAlignment.P1)) {
            names.addAll(GameContext.get().getDeckManager().getDraftedDeck(PieceAlignment.P1));
        } else {
            String[] fallbackDeck = {"Rogue", "Fairy", "Wind Spirit", "Big Toad", "Sniper",
                    "Barbarian", "King", "Charger", "Crossbowman", "Skeleton Bomber", "Warp Mage"};
            for (int i = 0; i < 24; i++) names.add(fallbackDeck[i % fallbackDeck.length]);
        }
        return names;
    }

    /** The bot's deck is not drafted today — same fixed rotation as before ARCH-028. */
    private static List<String> p2DeckNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 10; i++) names.add((i % 2 == 0) ? "Rifleman" : "Rogue");
        return names;
    }
}
