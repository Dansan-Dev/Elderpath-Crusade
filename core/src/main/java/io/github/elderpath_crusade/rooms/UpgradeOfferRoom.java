package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.run.RunCardModification;
import io.github.elderpath_crusade.run.RunNodeType;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * An ordinary Battle win's reward screen (2026-11-12 fix): {@code VictoryRoom} sends every
 * non-Elite, non-Boss win here — Boss keeps its own Elder pick ({@link ElderPickRoom}), Elite its
 * own relic/card-modification roll ({@link EliteRewardRoom}); an ordinary Battle previously gave
 * nothing at all ({@code VictoryRoom}'s switch just went straight back to {@link RunRoom}). This
 * class itself predates that wiring (UPG-005) and originally covered all three node types with
 * one relic/new-card/stat-boost screen before Elders (ELDER-005) took over Boss and
 * {@code EliteRewardRoom} took over Elite — kept unchanged otherwise, so its {@code ELITE}
 * branch below is now effectively dead by construction (nothing calls {@link #get} with
 * {@code RunNodeType.ELITE} any more) rather than removed, since it's harmless and still has its
 * own RunManagerTest-level coverage (offerNewCardChoices/offerRelicChoices/grantNewCard/
 * addRelic/upgradeCard all stay wired and tested).
 *
 * <p>Shown after winning a battle node, before returning to the map — offers one reward, applies
 * the pick to the active run via {@link RunManager}, then continues to {@link RunRoom}. Always
 * skippable. GUI-only; no headless test is possible here (see elderpath-testing.md).
 *
 * <p>For the ordinary Battle case this room is actually reached with today: offers a card
 * reward — 3 new cards drafted from {@link RunManager#offerNewCardChoices}, added to the deck via
 * {@link RunManager#grantNewCard} — or, if the draft pool has nothing new left to offer, falls
 * back to two stat-boost options for one randomly chosen owned card instead, so the room never
 * has nothing to offer.
 */
public class UpgradeOfferRoom extends Room {
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final RunManager runManager;
    private final Text header;
    private final ButtonList buttonList;
    private final Button skipButton;

    private UpgradeOfferRoom(RunNodeType clearedNodeType) {
        super();
        runManager = GameContext.get().getRunManager();

        header = new Text("Choose a Reward", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        buttonList = new ButtonList();
        buildRewardOptions(clearedNodeType);

        skipButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Skip", FontType.SILKSCREEN,
                FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 120, BUTTON_HEIGHT, 0)
                .withOnClick((e) -> continueRun(), ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(skipButton);

        layoutContents();
    }

    private void buildRewardOptions(RunNodeType clearedNodeType) {
        if (clearedNodeType == RunNodeType.ELITE) {
            List<String> relicChoices = runManager.offerRelicChoices(2);
            if (!relicChoices.isEmpty()) {
                for (String relicId : relicChoices) {
                    addOption(relicId, () -> {
                        runManager.addRelic(relicId);
                        continueRun();
                    });
                }
                return;
            }
        }

        List<String> cardChoices = runManager.offerNewCardChoices(3);
        if (!cardChoices.isEmpty()) {
            for (String cardName : cardChoices) {
                addOption(cardName, () -> {
                    runManager.grantNewCard(cardName);
                    continueRun();
                });
            }
            return;
        }

        addCardUpgradeOptions();
    }

    /**
     * Fallback when nothing new is left to draft/offer — upgrade one randomly chosen owned
     * card instead. BUG-051: must only pick a summon — a stat-delta modification needs a
     * {@code PieceDefinition} to apply to, which a spell has none of (same BUG-045 constraint
     * {@code CardModTargetRoom}/{@code ElderPickRoom} already respect); modifying one anyway
     * built a synthetic card name {@code MatchSetup} could never resolve, crashing the next
     * battle. Skips straight to the usual continuation if the deck is somehow all spells.
     */
    private void addCardUpgradeOptions() {
        List<RunCardInstance> eligible = runManager.getRunState().deck().stream()
                .filter(instance -> CardCatalog.isSummon(instance.cardName()))
                .toList();
        if (eligible.isEmpty()) {
            continueRun();
            return;
        }
        RunCardInstance target = eligible.get(new Random().nextInt(eligible.size()));
        String instanceId = target.instanceId();

        addOption("+1 Damage: " + target.cardName(), () -> {
            runManager.upgradeCard(instanceId, new RunCardModification(Map.of("damage", 1), List.of()));
            continueRun();
        });
        addOption("+1 Health: " + target.cardName(), () -> {
            runManager.upgradeCard(instanceId, new RunCardModification(Map.of("health", 1), List.of()));
            continueRun();
        });
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

    private void continueRun() {
        GameContext.get().getRoomManager().gotoRoom(RunRoom::get);
    }

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        int screenCenterX = screenCenter[0];
        int screenCenterY = screenCenter[1];

        MenuLayout.centerHeader(header, 100);
        buttonList.alignButtonsAcrossYAxis(BUTTON_SPACING, screenCenterX, screenCenterY);

        skipButton.getBounds().setX(screenCenterX - skipButton.getBounds().getWidth() / 2);
        skipButton.getBounds().setY(screenCenterY - (buttonList.getRenderables().size() * BUTTON_SPACING) / 2 - 60);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static UpgradeOfferRoom get(RunNodeType clearedNodeType) {
        return new UpgradeOfferRoom(clearedNodeType);
    }
}
