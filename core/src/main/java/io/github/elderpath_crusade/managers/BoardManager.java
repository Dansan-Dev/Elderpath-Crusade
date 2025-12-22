package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.game_objects.board.Board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Centralized manager for tracking and providing access to active Boards.
 * Avoids inefficient O(N) scans through GraphicsManager.renderables.
 */
public final class BoardManager {
    private static Board activeBoard;

    private BoardManager() {}

    public static void setBoard(Board board) {
        activeBoard = board;
    }

    public static Board getBoard() {
        return activeBoard;
    }

    public static void clear() {
        activeBoard = null;
    }
}
