package io.github.elderpath_crusade.run;

/** Node types for v1 — Battle, Elite Battle, Rest, Boss. No Shop/Event nodes for v1. */
public enum RunNodeType {
    BATTLE,
    ELITE,
    REST,
    BOSS;

    /** Whether this node type is a fight (everything except Rest). */
    public boolean isBattle() {
        return this != REST;
    }
}
