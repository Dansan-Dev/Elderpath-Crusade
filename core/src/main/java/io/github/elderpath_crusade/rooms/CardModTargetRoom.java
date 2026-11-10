package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.run.CardModificationCatalog;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;

/**
 * Second step of an Elite's card-modification reward (ELITE-001) — the player already picked
 * which {@link CardModificationCatalog.Entry} to apply ({@link CardModTypeRoom}); this lists
 * every card in the current deck, plus every Elder (BUG-044 — an Elder is a real, re-summonable
 * card too, not excluded from this reward just because it's retired from the drawable deck), as
 * a target. Picking one calls {@link RunManager#upgradeCard}, then continues to {@link RunRoom}.
 * A spell card in the deck is never offered (BUG-045) — there's no {@link
 * io.github.elderpath_crusade.data.PieceDefinition} to apply a stat delta to; every Elder
 * already is a summon (promoting one isn't offered otherwise, see {@link ElderPickRoom}), so
 * the Elder loop needs no equivalent filter.
 */
public class CardModTargetRoom extends Room {
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final ButtonList buttonList;

    private CardModTargetRoom(CardModificationCatalog.Entry chosenMod) {
        super();
        RunManager runManager = GameContext.get().getRunManager();

        header = new Text("Apply " + chosenMod.name() + " To:", FontType.SILKSCREEN, 0, 0, 0,
                ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        buttonList = new ButtonList();
        for (RunCardInstance instance : runManager.getRunState().deck()) {
            if (!CardCatalog.isSummon(instance.cardName())) continue; // BUG-045 — a spell has no stats to modify
            addCardOption(runManager, chosenMod, instance, false);
        }
        for (RunCardInstance instance : runManager.getRunState().elders()) {
            addCardOption(runManager, chosenMod, instance, true);
        }

        layoutContents();
    }

    private void addCardOption(RunManager runManager, CardModificationCatalog.Entry chosenMod, RunCardInstance instance, boolean isElder) {
        String instanceId = instance.instanceId();
        String label = instance.cardName() + (instance.isModified() ? " (modified)" : "") + (isElder ? " (Elder)" : "");
        addOption(label, () -> {
            runManager.upgradeCard(instanceId, chosenMod.delta());
            GameContext.get().getRoomManager().gotoRoom(RunRoom::get);
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

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        MenuLayout.centerHeader(header, 100);
        buttonList.alignButtonsAcrossYAxis(BUTTON_SPACING, screenCenter[0], screenCenter[1]);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get(CardModificationCatalog.Entry chosenMod) {
        RunManager runManager = GameContext.get().getRunManager();
        List<RunCardInstance> deck = runManager.getRunState().deck();
        boolean anyEligible = deck.stream().anyMatch(i -> CardCatalog.isSummon(i.cardName()))
                || !runManager.getRunState().elders().isEmpty();
        // Nothing to apply this reward to (an all-spell deck with no Elders yet, BUG-045) —
        // skip straight to the usual continuation rather than show an empty screen with no
        // buttons at all.
        if (!anyEligible) return RunRoom.get();
        return new CardModTargetRoom(chosenMod);
    }
}
