package io.github.elderpath_crusade.audio;

/**
 * Every sound effect the game plays (FEAT-017). Which file(s) each one uses lives in
 * {@code assets/data/sfx.yaml}, keyed by these names — so a sound can be swapped by ear without
 * touching code. An effect with no yaml entry (or a missing file) is simply silent.
 */
public enum SoundEffect {
    // UI
    UI_CLICK,
    SELECT,
    REJECTED,
    // Board
    MOVE,
    FORCED_MOVE,
    ATTACK,
    DAMAGE,
    HEAL,
    DEATH,
    SUMMON,
    // Cards
    SPELL_CAST,
    CARD_DRAW,
    SHUFFLE,
    // Match flow
    YOUR_TURN,
    VICTORY,
    DEFEAT
}
