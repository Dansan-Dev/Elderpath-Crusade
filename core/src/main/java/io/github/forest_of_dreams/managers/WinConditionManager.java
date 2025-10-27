package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.managers.Game;
import io.github.forest_of_dreams.multiplayer.EventBus;
import io.github.forest_of_dreams.multiplayer.GameEvent;
import io.github.forest_of_dreams.multiplayer.GameEventType;
import io.github.forest_of_dreams.utils.Logger;
import io.github.forest_of_dreams.rooms.VictoryRoom;
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

    private WinConditionManager() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        Consumer<GameEvent> listener = WinConditionManager::handleEvent;
        EventBus.register(GameEventType.PIECE_MOVED, listener);
        EventBus.register(GameEventType.PIECE_SPAWNED, listener);
    }

    private static void handleEvent(GameEvent evt) {
        if (gameWon) return;
        Map<String, Object> data = evt.getData();
        if (data == null) return;
        // Owner alignment is provided as string name in events we emit
        Object ownerObj = data.get("owner");
        if (ownerObj == null) return;
        PieceAlignment owner;
        try {
            owner = PieceAlignment.valueOf(ownerObj.toString());
        } catch (IllegalArgumentException ex) {
            return;
        }
        // Destination row key differs per event
        Integer destRow = null;
        if (evt.getType() == GameEventType.PIECE_MOVED) {
            Object v = data.get("toRow");
            if (v instanceof Integer i) destRow = i; else if (v != null) {
                try { destRow = Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
            }
        } else if (evt.getType() == GameEventType.PIECE_SPAWNED) {
            Object v = data.get("row");
            if (v instanceof Integer i) destRow = i; else if (v != null) {
                try { destRow = Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
            }
        }
        if (destRow == null) return;

        Integer rows = getActiveBoardRows();
        if (rows == null) return; // can't evaluate without a board

        // Ignore neutral or undefined alignments
        if (owner == PieceAlignment.NEUTRAL) {
            return;
        }
        boolean win = false;
        if (owner == PieceAlignment.P1 && destRow == rows - 1) {
            win = true;
        } else if (owner == PieceAlignment.P2 && destRow == 0) {
            win = true;
        }
        if (win) {
            triggerWin(owner);
        }
    }

    private static Integer getActiveBoardRows() {
        List<Renderable> renderables = GraphicsManager.getRenderables();
        for (Renderable r : renderables) {
            if (r instanceof Board b) {
                return b.getROWS();
            }
        }
        return null;
    }

    private static void triggerWin(PieceAlignment winner) {
        if (gameWon) return;
        gameWon = true;
        String msg = "VICTORY: " + winner.name();
        System.out.println(msg);
        Logger.log("Win", msg);
        // Show a simple Victory screen after a brief delay to let animations/events settle
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                Game.gotoRoom(() -> VictoryRoom.get(winner));
            }
        }, 0.6f);
    }
}
