package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.match.CardCatalog;
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
 * Choose a new Elder (ELDER-004/005, re-triggered by Phase 3.6) — shared by both the very first
 * pick, right after a fresh run's deck is drafted ({@link RunDraftRoom}), and every later pick,
 * after clearing a non-final floor's boss ({@code VictoryRoom}; Phase 3.6 moved this off *every*
 * non-terminal win, which is what ELDER-005 originally wired it to). Lists every card currently
 * in the deck as a candidate; picking one calls {@link RunManager#promoteToElder}, which
 * retires it from the deck into Elder status and raises (or leaves unchanged) every future
 * battle's card-lock wait — see {@code RunManager}'s class doc.
 *
 * <p>{@code advanceFloorAfterPick} is true only for the post-boss case — after promoting (or
 * skipping, if the deck has nothing eligible), it also calls {@link RunManager#advanceToNextFloor}
 * before continuing to {@link RunRoom}. The very first pick (from {@link RunDraftRoom}) passes
 * false: the run just started, already on floor 0, nothing to advance from.
 *
 * <p>A deck with only one card left has nothing eligible to promote without emptying it
 * (RunState.addElder's own guard) — skips straight to the usual continuation rather than show
 * an empty choice. A spell card is never offered either (BUG-045) — only a summon has a real
 * {@link io.github.elderpath_crusade.data.PieceDefinition} to spawn as an on-board Elder;
 * promoting a spell would retire it from the deck and then silently never spawn anything.
 */
public class ElderPickRoom extends Room {
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final ButtonList buttonList;

    private ElderPickRoom(boolean advanceFloorAfterPick) {
        super();
        RunManager runManager = GameContext.get().getRunManager();

        header = new Text("Choose Your Elder", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        buttonList = new ButtonList();
        for (RunCardInstance instance : runManager.getRunState().deck()) {
            if (!CardCatalog.isSummon(instance.cardName())) continue; // BUG-045 — a spell can't be an Elder
            String instanceId = instance.instanceId();
            String label = instance.cardName() + (instance.isModified() ? " (modified)" : "");
            addOption(label, () -> {
                runManager.promoteToElder(instanceId);
                continueRun(runManager, advanceFloorAfterPick);
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

    private static void continueRun(RunManager runManager, boolean advanceFloorAfterPick) {
        if (advanceFloorAfterPick) runManager.advanceToNextFloor();
        GameContext.get().getRoomManager().gotoRoom(RunRoom::get);
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

    public static Room get(boolean advanceFloorAfterPick) {
        RunManager runManager = GameContext.get().getRunManager();
        List<RunCardInstance> deck = runManager.getRunState().deck();
        // Nothing eligible either way: emptying the deck isn't allowed (RunState.addElder), or
        // every card left is a spell (BUG-045, never offered) — skip straight to the usual
        // continuation rather than show an empty choice.
        boolean anyEligible = deck.size() > 1 && deck.stream().anyMatch(i -> CardCatalog.isSummon(i.cardName()));
        if (!anyEligible) {
            if (advanceFloorAfterPick) runManager.advanceToNextFloor();
            return RunRoom.get();
        }
        return new ElderPickRoom(advanceFloorAfterPick);
    }
}
