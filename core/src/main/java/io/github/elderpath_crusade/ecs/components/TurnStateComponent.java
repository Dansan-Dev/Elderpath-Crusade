package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;
import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Singleton component holding authoritative turn state.
 * Lives on a dedicated "game state" entity.
 */
public class TurnStateComponent implements Component {
    public PieceAlignment currentPlayer = PieceAlignment.P1;
    public int turnCount = 0;
    public boolean started = false;
    public boolean waitingForNextPlayer = false;
}
