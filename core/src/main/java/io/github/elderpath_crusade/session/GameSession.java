package io.github.elderpath_crusade.session;

import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import lombok.Getter;

@Getter
public class GameSession {
    private final GameMode mode;
    private final Board board;
    private PieceAlignment currentPlayer;
    private int turnNumber;
    private boolean active;

    private GameSession(GameMode mode, Board board) {
        this.mode = mode;
        this.board = board;
        this.currentPlayer = PieceAlignment.P1;
        this.turnNumber = 0;
        this.active = false;
    }

    public static GameSession create(GameMode mode, Board board) {
        return new GameSession(mode, board);
    }

    public void start() {
        this.active = true;
        this.turnNumber = 1;
    }

    public void end() {
        this.active = false;
    }

    public void advanceTurn(PieceAlignment nextPlayer) {
        this.currentPlayer = nextPlayer;
        this.turnNumber++;
    }
}
