package io.github.elderpath_crusade.enums;

public enum GamePieceData {
    TYPE,
    POSITION,
    MOVE_CAUSE, // "MANUAL" when moved via standard move, "ABILITY" when moved by an ability
    STUN_TURNS_REMAINING; // Number of turns remaining in stun state (0 = not stunned)
}
