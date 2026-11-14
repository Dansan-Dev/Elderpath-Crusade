package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.run.CardModificationCatalog;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.run.RunCardModification;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Recruitment Site node (RECRUIT-001, replacing Rest — user's design, 2026-11-12): "you are
 * recruiting a mercenary for your tribe, then someone in your tribe does not like the new
 * member and chooses to leave." Two steps: pick one of 2 random new cards, each already
 * carrying a random {@link CardModificationCatalog} upgrade (the mercenary's own gear) —
 * {@link #get} rolls both; picking one calls {@link RunManager#grantNewCard(String,
 * io.github.elderpath_crusade.run.RunCardModification)} and continues to {@link
 * RecruitmentDepartureRoom} to pick who leaves.
 *
 * <p>The departure candidates are rolled from the deck as it stood *before* the new recruit is
 * added — the recruit can never be the one who leaves, matching the story (an existing member
 * objects to the newcomer, not the newcomer to themselves). If the deck has fewer than 2 cards
 * to begin with, there's no one to offer as a departure choice — the new card is still granted,
 * but the room skips straight to {@link RunRoom} afterward.
 */
public class RecruitmentSiteRoom extends Room {
    private static final int NEW_CARD_CHOICES = 2;
    private static final int BUTTON_WIDTH = 280;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final Text flavor;
    private final ButtonList buttonList;

    private RecruitmentSiteRoom(List<String> newCardChoices, List<RunCardInstance> departureCandidates) {
        super();
        RunManager runManager = GameContext.get().getRunManager();
        Random rng = new Random();

        header = new Text("Recruitment Site", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        flavor = new Text("A mercenary offers their services to the tribe.", FontType.SILKSCREEN, 0, 0, 0,
                ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.BODY_MEDIUM);
        addContent(flavor);

        buttonList = new ButtonList();
        List<CardModificationCatalog.Entry> catalog = CardModificationCatalog.all();
        for (String cardName : newCardChoices) {
            // BUG-051: a modification needs a PieceDefinition to apply its stat delta to — a
            // spell (e.g. Frostbolt) has none (same BUG-045 constraint CardModTargetRoom/
            // ElderPickRoom already respect). Rolling an upgrade for one anyway built a
            // synthetic card name MatchSetup could never resolve, crashing the next battle.
            // A spell recruit simply arrives as-is, no upgrade to offer.
            boolean canUpgrade = CardCatalog.isSummon(cardName);
            RunCardModification upgradeDelta = RunCardModification.NONE;
            String label = cardName;
            if (canUpgrade) {
                CardModificationCatalog.Entry upgrade = catalog.get(rng.nextInt(catalog.size()));
                upgradeDelta = upgrade.delta();
                label = cardName + " — " + upgrade.name();
            }
            RunCardModification finalUpgrade = upgradeDelta;
            addOption(label, () -> {
                runManager.grantNewCard(cardName, finalUpgrade);
                GameContext.get().getRoomManager().gotoRoom(() -> RecruitmentDepartureRoom.get(departureCandidates));
            });
        }

        layoutContents();
    }

    private void addOption(String label, Runnable onClick) {
        Button button = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), label, FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, 0)
                .withOnClick((e) -> onClick.run(), ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        buttonList.addButton(button);
        addContent(button);
    }

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        MenuLayout.centerHeader(header, 100);
        MenuLayout.centerHeader(flavor, 150);
        buttonList.alignButtonsAcrossYAxis(BUTTON_SPACING, screenCenter[0], screenCenter[1]);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get() {
        RunManager runManager = GameContext.get().getRunManager();
        List<String> newCardChoices = runManager.offerNewCardChoices(NEW_CARD_CHOICES);
        if (newCardChoices.isEmpty()) return RunRoom.get(); // the draft pool is exhausted — nothing to recruit
        return new RecruitmentSiteRoom(newCardChoices, departureCandidates(runManager));
    }

    /** Up to 2 random *pre-existing* deck cards — rolled before the new recruit is added, so it can never be one of them. */
    private static List<RunCardInstance> departureCandidates(RunManager runManager) {
        List<RunCardInstance> deck = new ArrayList<>(runManager.getRunState().deck());
        if (deck.size() < 2) return List.of();
        Collections.shuffle(deck);
        return deck.subList(0, 2);
    }
}
