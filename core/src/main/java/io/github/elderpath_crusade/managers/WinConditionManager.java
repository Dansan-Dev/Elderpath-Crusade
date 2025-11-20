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

    private WinConditionManager() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        Consumer<GameEvent> listener = WinConditionManager::handleEvent;
        EventBus.register(GameEventType.ACTIVE_MOVEMENT, listener);
        EventBus.register(GameEventType.FORCED_MOVEMENT, listener);
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
        GameEventType eventType = evt.getType();
        if (eventType == GameEventType.ACTIVE_MOVEMENT || eventType == GameEventType.FORCED_MOVEMENT) {
            Object v = data.get("toRow");
            if (v instanceof Integer i) destRow = i; else if (v != null) {
                try { destRow = Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
            }
        } else if (eventType == GameEventType.PIECE_SPAWNED) {
            Object v = data.get("row");
            if (v instanceof Integer i) destRow = i; else if (v != null) {
                try { destRow = Integer.parseInt(v.toString()); } catch (NumberFormatException ignored) {}
            }
        }
        if (destRow == null) return;

        Integer rows = getActiveBoardRows();
        if (rows == null) return; // can't evaluate without a board
        
        // Get the active board to check flip state
        Board activeBoard = getActiveBoard();
        if (activeBoard == null) return;
        
        // Check if board is currently flipped (for P2's turn in LOCAL_MATCH)
        boolean flipped = activeBoard.isFlipped();

        // Ignore neutral or undefined alignments
        if (owner == PieceAlignment.NEUTRAL) {
            return;
        }
        
        // Win condition: player wins when reaching opponent's home row
        // When not flipped: P1's home row = 0, P2's home row = rows-1
        //   - P1 wins when reaching rows-1 (P2's home row)
        //   - P2 wins when reaching 0 (P1's home row)
        // When flipped: P1's home row = rows-1, P2's home row = 0
        //   - P1 wins when reaching 0 (P2's home row)
        //   - P2 wins when reaching rows-1 (P1's home row)
        boolean win = false;
        if (owner == PieceAlignment.P1) {
            // P1 wins when reaching P2's home row
            win = flipped ? (destRow == 0) : (destRow == rows - 1);
        } else if (owner == PieceAlignment.P2) {
            // P2 wins when reaching P1's home row
            win = flipped ? (destRow == rows - 1) : (destRow == 0);
        }
        if (win) {
            triggerWin(owner);
        }
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
        if (gameWon) return;
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
//            GameManager.pause();
            // Lock interactions so ESC and other inputs are ignored during the transition delay
            GameManager.lockInteractions();
        } catch (Exception ignored) {}
        // Show a simple Victory screen after a brief delay to let animations/events settle
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                // Restore interactivity for the Victory UI before switching rooms
                try {
                    GameManager.unlockInteractions();
//                    GameManager.unpause();
                } catch (Exception ignored) {}
                RoomManager.gotoRoom(() -> VictoryRoom.get(winner));
            }
        }, 0.6f);
    }
}
