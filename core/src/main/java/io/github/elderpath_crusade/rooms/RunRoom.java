package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.run.RunMap;
import io.github.elderpath_crusade.run.RunNode;
import io.github.elderpath_crusade.run.RunNodeType;
import io.github.elderpath_crusade.run.RunState;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Run room shell (RUN-003) + clickable branching map screen (MAP-002, roguelike-run-plan.md
 * Phase 2) — shows the active run's map as one button per node (colored by type, positioned by
 * floor/column), and status text for the current node. A node button is clickable in exactly
 * one of two situations: it's the current node and it hasn't been fought yet ("Start Node" —
 * hands off into MatchStartingRoom -> RunBattleRoom), or the current node is already cleared and
 * this node is one of the reachable choices for where to go next (RunManager.advanceToNextNode).
 * Every other node is disabled: visited nodes stay dimmed to mark progress, unvisited/
 * unreachable ones are locked. VictoryRoom reports a battle's outcome back to RunManager and
 * returns here — already on the next node if Rest (no fight), or cleared and awaiting a pick
 * otherwise. {@link RunMapEdgeRenderer} (BUG-041) draws the graph's actual connections between
 * node buttons, behind them, so the branching structure is visible rather than just each node's
 * own clickable/locked color.
 */
public class RunRoom extends Room {
    private static final List<String> DEFAULT_STARTING_DECK =
            List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub", "Warp Mage");
    private static final int HEADER_TOP_OFFSET = 60;
    private static final int STATUS_TOP_OFFSET = 110;
    private static final int ELDERS_TOP_OFFSET = 145;
    private static final int MAP_TOP_MARGIN = 180;
    private static final int MAP_BOTTOM_MARGIN = 90;
    private static final int NODE_WIDTH = 110;
    private static final int NODE_HEIGHT = 50;

    private final Text header;
    private final Text status;
    private final Text elders;
    private final Button backButton;
    private final List<List<RunNode>> floors;
    private final Map<Integer, Button> nodeButtons = new LinkedHashMap<>();

    private RunRoom() {
        super();
        GameContext.get().getMusicManager().playLoopingMusic("Evening_Harmony.mp3");

        RunManager runManager = GameContext.get().getRunManager();
        if (!runManager.hasActiveRun()) {
            // Defensive fallback only — the real flow always goes through DifficultySelectRoom
            // -> RunDraftRoom -> ElderPickRoom (ELDER-004) before ever reaching here. Still
            // promotes a first Elder so this degenerate path doesn't leave the run with none.
            runManager.startRun(RunDifficulty.DEFAULT, DEFAULT_STARTING_DECK);
            runManager.promoteToElder(runManager.getRunState().deck().get(0).instanceId());
        }
        RunState state = runManager.getRunState();
        floors = computeFloors(state.map());

        header = new Text("Roguelike Run", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        status = new Text(statusText(state), FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.BODY_MEDIUM);
        addContent(status);

        elders = new Text(eldersText(runManager), FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.CAPTION);
        addContent(elders);

        buildNodeButtons(state);
        addContent(new RunMapEdgeRenderer(state.map(), nodeButtons, state.visitedNodeIds()));

        backButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Main Menu", FontType.SILKSCREEN,
                FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 160, 60, 0)
            .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(MainMenuRoom::get), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(backButton);

        layoutContents();
    }

    private void buildNodeButtons(RunState state) {
        boolean cleared = state.isCurrentNodeCleared();
        int currentId = state.currentNode().id();
        Set<Integer> visited = state.visitedNodeIds();
        Set<Integer> reachable = new HashSet<>();
        for (RunNode n : state.reachableNodes()) reachable.add(n.id());

        for (List<RunNode> floor : floors) {
            for (RunNode node : floor) {
                boolean isCurrentUnfought = !cleared && node.id() == currentId;
                boolean isPickable = cleared && reachable.contains(node.id());
                boolean clickable = isCurrentUnfought || isPickable;
                boolean wasVisited = visited.contains(node.id());

                Button button = Button.fromColor(colorFor(node.type(), wasVisited, clickable), nodeLabel(node),
                        FontType.SILKSCREEN, FontSize.CAPTION.getSize(), 0, 0, NODE_WIDTH, NODE_HEIGHT, 1)
                    .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                    .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                    .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
                button.setDisabled(!clickable);
                if (clickable) {
                    button.withOnClick((e) -> onNodeClicked(node, isCurrentUnfought), ClickableEffectData.getImmediate());
                }
                addContent(button);
                nodeButtons.put(node.id(), button);
            }
        }
    }

    private void onNodeClicked(RunNode node, boolean isCurrentUnfought) {
        if (isCurrentUnfought) {
            startCurrentNode();
        } else {
            GameContext.get().getRunManager().advanceToNextNode(node.id());
            if (node.type().isBattle()) {
                // Battle/Elite/Boss: advanceToNextNode leaves it current-and-unfought (it never
                // auto-clears a fight the way it does Rest) — go straight into it instead of
                // redrawing this room just to make the player click the same node again.
                startCurrentNode();
            } else {
                GameContext.get().getRoomManager().gotoRoom(RunRoom::get);
            }
        }
    }

    private static Color colorFor(RunNodeType type, boolean visited, boolean clickable) {
        if (visited) return ColorSettings.RUN_NODE_VISITED.getColor();
        if (!clickable) return ColorSettings.RUN_NODE_LOCKED.getColor();
        return switch (type) {
            case BATTLE -> ColorSettings.RUN_NODE_BATTLE.getColor();
            case ELITE -> ColorSettings.RUN_NODE_ELITE.getColor();
            case REST -> ColorSettings.RUN_NODE_REST.getColor();
            case BOSS -> ColorSettings.RUN_NODE_BOSS.getColor();
        };
    }

    private static String nodeLabel(RunNode node) {
        return switch (node.type()) {
            case BATTLE -> "Battle";
            case ELITE -> "Elite";
            case REST -> "Rest";
            case BOSS -> "Boss";
        };
    }

    private static String statusText(RunState state) {
        if (state.isCurrentNodeCleared()) {
            return "Node cleared — choose your next node";
        }
        return "Current node: " + nodeLabel(state.currentNode()) + " — click it to start";
    }

    /** The run's current Elders and the card-lock wait they add up to — otherwise invisible once picked. */
    private static String eldersText(RunManager runManager) {
        List<RunCardInstance> currentElders = runManager.getRunState().elders();
        if (currentElders.isEmpty()) return "Elders: none";
        String names = currentElders.stream().map(RunCardInstance::cardName).collect(Collectors.joining(", "));
        int lockTurns = runManager.elderCardLockTurns();
        return "Elders: " + names + " (cards locked " + lockTurns + " turn" + (lockTurns == 1 ? "" : "s") + " each battle)";
    }

    /**
     * Assigns each node a column ("floor") via {@link RunMap#depths()} — the same notion of
     * depth DIFF-001's difficulty formula scales against, so the map's layout and its actual
     * difficulty always agree on what "deeper" means.
     */
    private static List<List<RunNode>> computeFloors(RunMap map) {
        Map<Integer, Integer> depths = map.depths();
        int maxFloor = depths.values().stream().max(Integer::compareTo).orElse(0);
        List<List<RunNode>> floors = new ArrayList<>();
        for (int f = 0; f <= maxFloor; f++) floors.add(new ArrayList<>());
        for (RunNode node : map.nodes()) {
            Integer f = depths.get(node.id());
            if (f != null) floors.get(f).add(node);
        }
        return floors;
    }

    private void startCurrentNode() {
        RunManager runManager = GameContext.get().getRunManager();
        GameContext.get().getRoomManager().gotoRoom(() ->
                MatchStartingRoom.get(
                        () -> ClientMatchSession.startLocal(runManager.buildSpecForCurrentNode()),
                        RunBattleRoom::get));
    }

    private void layoutContents() {
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        MenuLayout.centerHeader(header, HEADER_TOP_OFFSET);
        MenuLayout.centerHeader(status, STATUS_TOP_OFFSET);
        MenuLayout.centerHeader(elders, ELDERS_TOP_OFFSET);
        layoutMap(screenW, screenH);

        int screenCenterX = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0];
        backButton.getBounds().setX(screenCenterX - backButton.getBounds().getWidth() / 2);
        backButton.getBounds().setY(30);
    }

    private void layoutMap(int screenW, int screenH) {
        int mapTop = screenH - MAP_TOP_MARGIN;
        int mapBottom = MAP_BOTTOM_MARGIN;
        int mapHeight = Math.max(1, mapTop - mapBottom);
        int floorCount = floors.size();

        for (int f = 0; f < floorCount; f++) {
            int x = (screenW * (f + 1)) / (floorCount + 1) - NODE_WIDTH / 2;
            List<RunNode> floorNodes = floors.get(f);
            int rowCount = floorNodes.size();
            for (int i = 0; i < rowCount; i++) {
                RunNode node = floorNodes.get(i);
                Button button = nodeButtons.get(node.id());
                if (button == null) continue;
                int y = mapBottom + (mapHeight * (i + 1)) / (rowCount + 1) - NODE_HEIGHT / 2;
                button.getBounds().setX(x);
                button.getBounds().setY(y);
            }
        }
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static RunRoom get() {
        return new RunRoom();
    }
}
