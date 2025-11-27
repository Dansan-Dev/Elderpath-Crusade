package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.utils.Logger;
import io.github.elderpath_crusade.rooms.VictoryRoom;
import com.badlogic.gdx.utils.Timer;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Centralized win condition watcher.
 * Triggers when a piece reaches the opponent's summoning row:
 * - P1 wins when a P1 piece reaches the last row (ROWS-1).
 * - P2 wins when a P2 piece reaches row 0.
 *
 * On trigger: prints a victory message and exits the app.
 */
public final class WinConditionManager {
    private static boolean initialized = false;
    private static boolean gameWon = false;

    /**
     * Reset the win-condition state so a new game can trigger victories again.
     * Listeners remain registered; only the guard flag is cleared.
     */
    public static void reset() {
        gameWon = false;
    }

    private WinConditionManager() {
    }

    public static void initialize() {
        if (initialized)
            return;
        initialized = true;

        List<GameEventType> relevantEventTypes = List.of(
                GameEventType.ACTIVE_MOVEMENT,
                GameEventType.FORCED_MOVEMENT,
                GameEventType.PIECE_SPAWNED);

        Consumer<GameEvent> listener = WinConditionManager::handleEvent;
        relevantEventTypes.forEach(evt -> EventBus.register(evt, listener));
    }

    private static void handleEvent(GameEvent evt) {
        if (gameWon)
            return;
        Map<String, Object> data = evt.getData();
        if (data == null)
            return;

        PieceAlignment alignment = extractAlignment(data);
        if (alignment == null || alignment == PieceAlignment.NEUTRAL)
            return;

        Integer destRow = extractDestRow(evt, data);
        if (destRow == null)
            return;

        Integer rows = getActiveBoardRows();
        if (rows == null)
            return;

        Board activeBoard = getActiveBoard();
        if (activeBoard == null)
            return;

        if (checkWinCondition(alignment, destRow, rows, activeBoard.isFlipped())) {
            triggerWin(alignment);
        }
    }

    private static PieceAlignment extractAlignment(Map<String, Object> data) {
        Object ownerObj = data.get("owner");
        if (ownerObj == null)
            return null;
        try {
            return PieceAlignment.valueOf(ownerObj.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Integer extractDestRow(GameEvent evt, Map<String, Object> data) {
        Integer destRow = null;
        GameEventType eventType = evt.getType();
        if (eventType == GameEventType.ACTIVE_MOVEMENT || eventType == GameEventType.FORCED_MOVEMENT) {
            destRow = parseInteger(data.get("toRow"));
        } else if (eventType == GameEventType.PIECE_SPAWNED) {
            destRow = parseInteger(data.get("row"));
        }
        return destRow;
    }

    private static Integer parseInteger(Object v) {
        if (v instanceof Integer i)
            return i;
        if (v != null) {
            try {
                return Integer.parseInt(v.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static boolean checkWinCondition(PieceAlignment alignment, int destRow, int rows, boolean flipped) {
        if (alignment == PieceAlignment.P1) {
            return flipped ? (destRow == 0) : (destRow == rows - 1);
        } else if (alignment == PieceAlignment.P2) {
            return flipped ? (destRow == rows - 1) : (destRow == 0);
        }
        return false;
    }

    private static Integer getActiveBoardRows() {
        Board board = getActiveBoard();
        return (board != null) ? board.getROWS() : null;
    }

    private static Board getActiveBoard() {
        List<Renderable> renderables = GraphicsManager.getRenderables();
        for (Renderable r : renderables) {
            if (r instanceof Board b) {
                return b;
            }
        }
        return null;
    }

    private static void triggerWin(PieceAlignment winner) {
        if (gameWon)
            return;
        gameWon = true;
        String msg = "VICTORY: " + winner.name();
        System.out.println(msg);
        Logger.log("Win", msg);
        // Immediately lock interactions and input during transition delay
        try {
            // Cancel any active multi-selection to avoid unintended resolves
            if (InteractionManager.hasActiveSelection()) {
                InteractionManager.cancelSelection();
            }
            // Pause the game: blocks InteractionManager clicks and halts bot actions
            // GameManager.pause();
            // Lock interactions so ESC and other inputs are ignored during the transition
            // delay
            GameManager.lockInteractions();
        } catch (Exception ignored) {
        }
        // Show a simple Victory screen after a brief delay to let animations/events
        // settle
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                // Restore interactivity for the Victory UI before switching rooms
                try {
                    GameManager.unlockInteractions();
                    // GameManager.unpause();
                } catch (Exception ignored) {
                }
                RoomManager.gotoRoom(() -> VictoryRoom.get(winner));
            }
        }, 0.6f);
    }
}
