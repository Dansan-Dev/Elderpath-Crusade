package io.github.elderpath_crusade.model.match;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.model.board.BoardModel;
import io.github.elderpath_crusade.model.player.PlayerModel;

/**
 * Pure model for a match. Holds board, players, and turn state.
 */
public class MatchModel {
    private final BoardModel board;
    private final PlayerModel player1;
    private final PlayerModel player2;
    private PieceAlignment currentTurn;
    private int turnCount;

    public MatchModel(BoardModel board, PlayerModel player1, PlayerModel player2) {
        this.board = board;
        this.player1 = player1;
        this.player2 = player2;
        this.currentTurn = PieceAlignment.P1;
        this.turnCount = 0;
    }

    public BoardModel getBoard() { return board; }
    public PlayerModel getPlayer1() { return player1; }
    public PlayerModel getPlayer2() { return player2; }
    public PieceAlignment getCurrentTurn() { return currentTurn; }
    public int getTurnCount() { return turnCount; }

    public PlayerModel getCurrentPlayer() {
        return currentTurn == PieceAlignment.P1 ? player1 : player2;
    }

    public PlayerModel getPlayer(PieceAlignment alignment) {
        return alignment == PieceAlignment.P1 ? player1 : player2;
    }

    public void nextTurn() {
        currentTurn = (currentTurn == PieceAlignment.P1) ? PieceAlignment.P2 : PieceAlignment.P1;
        turnCount++;
    }
}
