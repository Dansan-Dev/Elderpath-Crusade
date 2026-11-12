package io.github.elderpath_crusade.run;

/**
 * Node types for v1 — Battle, Elite Battle, Recruitment Site, Event, Boss. No Shop node for v1.
 *
 * <p>Rest (removed 2026-11-12, user's call) used to be the only non-battle node and did nothing
 * by itself — it auto-cleared on arrival and offered a hook point ({@code
 * RelicRunHook#onEnterRestNode}) no yaml-authored relic ever actually used. Recruitment and
 * Event replace it as the run's non-battle nodes, each with its own real, always-something-
 * happens effect — see {@code RecruitmentSiteRoom}/{@code EventRoom}.
 */
public enum RunNodeType {
    BATTLE,
    ELITE,
    RECRUITMENT,
    EVENT,
    BOSS;

    /** Whether this node type is a fight (everything except Recruitment/Event). */
    public boolean isBattle() {
        return this != RECRUITMENT && this != EVENT;
    }
}
