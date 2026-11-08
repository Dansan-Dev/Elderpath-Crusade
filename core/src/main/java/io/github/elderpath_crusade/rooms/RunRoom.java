package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.run.RunState;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;

/**
 * Run room shell (RUN-003, roguelike-run-plan.md Phase 1) — shows the active run's status and a
 * "Start Node" action handing off into MatchStartingRoom -> RunBattleRoom for the current node's
 * fight; VictoryRoom reports the outcome back to RunManager and returns here (continuing the
 * run, already advanced to the next node) or ends it. No real branching map yet (MAP-001/002) —
 * status shows the placeholder linear chain's progress, not a clickable graph.
 */
public class RunRoom extends Room {
    private static final List<String> DEFAULT_STARTING_DECK = List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub");
    private static final int HEADER_TOP_OFFSET = 100;
    private static final int STATUS_TOP_OFFSET = 180;

    private final Text header;
    private final Text status;
    private final Button startNodeButton;
    private final Button backButton;

    private RunRoom() {
        super();
        GameContext.get().getMusicManager().playLoopingMusic("Evening_Harmony.mp3");

        RunManager runManager = GameContext.get().getRunManager();
        if (!runManager.hasActiveRun()) {
            runManager.startRun(RunDifficulty.DEFAULT, DEFAULT_STARTING_DECK);
        }

        header = new Text("Roguelike Run", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        status = new Text(statusText(runManager.getRunState()), FontType.SILKSCREEN, 0, 0, 0,
                ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.BODY_MEDIUM);
        addContent(status);

        startNodeButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Start Node", FontType.SILKSCREEN,
                FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 220, 60, 0)
            .withOnClick((e) -> startCurrentNode(), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(startNodeButton);

        backButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Main Menu", FontType.SILKSCREEN,
                FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 160, 60, 0)
            .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(MainMenuRoom::get), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(backButton);

        layoutContents();
    }

    private static String statusText(RunState state) {
        return "Node " + state.visitedNodeIds().size() + " of " + state.map().nodes().size()
                + " — up next: " + state.currentNode().type()
                + " | Deck size: " + state.deck().size();
    }

    /**
     * Launches the current node's battle directly, bypassing RunManager's own
     * startCurrentNode/pollActiveNode/activeSession bookkeeping entirely: this room already
     * manages the session's whole lifecycle itself, the same way every other battle room does
     * (BattleRoom's own per-frame polling, VictoryRoom reporting the outcome via
     * reportNodeOutcome once the match ends). Mixing the two — launching through
     * RunManager.startCurrentNode() but never calling the matching pollActiveNode() to clear
     * its activeSession field — left that field permanently set after the first node, so every
     * later "Start Node" click failed with "a node is already in progress" (BUG-037, found via
     * a real playtest: won the first node, the second node's Start Node click crashed the
     * client). RunManager's startCurrentNode/pollActiveNode pair stays for a self-contained
     * headless caller (see RunManagerSessionTest) that never touches this path.
     */
    private void startCurrentNode() {
        RunManager runManager = GameContext.get().getRunManager();
        GameContext.get().getRoomManager().gotoRoom(() ->
                MatchStartingRoom.get(
                        () -> ClientMatchSession.startLocal(runManager.buildSpecForCurrentNode()),
                        RunBattleRoom::get));
    }

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        int screenCenterX = screenCenter[0];
        int screenCenterY = screenCenter[1];

        MenuLayout.centerHeader(header, HEADER_TOP_OFFSET);
        MenuLayout.centerHeader(status, STATUS_TOP_OFFSET);

        startNodeButton.getBounds().setX(screenCenterX - startNodeButton.getBounds().getWidth() / 2);
        startNodeButton.getBounds().setY(screenCenterY - 20);

        backButton.getBounds().setX(screenCenterX - backButton.getBounds().getWidth() / 2);
        backButton.getBounds().setY(screenCenterY - 100);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static RunRoom get() {
        return new RunRoom();
    }
}
