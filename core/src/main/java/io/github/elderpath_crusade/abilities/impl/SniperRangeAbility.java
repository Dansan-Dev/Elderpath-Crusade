package io.github.elderpath_crusade.abilities.impl;

import io.github.elderpath_crusade.abilities.PassiveAbility;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

/**
 * Sniper passive: +3 attack range to self.
 * Base range on pieces is 0 (melee classification). This passive increases effective range to 3.
 */
public class SniperRangeAbility implements PassiveAbility {
    private final StatsModifier mod;
    private MonsterGamePiece owner;

    public SniperRangeAbility() {
        this.mod = new StatsModifier();
        this.mod.source = this;
        this.mod.addRange = 3;
    }

    @Override
    public String getName() { return "Sniper Range"; }

    @Override
    public String getDescription() { return getAbilityDescription(); }

    public static String getAbilityDescription() { return "Ranged 3"; }

    @Override
    public StatsModifier getModifier() { return mod; }

    @Override
    public boolean isConditionMet(MonsterGamePiece owner, Board board) { return true; }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        this.owner = owner;
        if (owner != null) owner.getStatsAccumulator().add(mod);
    }

    @Override
    public void onDetach() {
        // Remove from owner if present
        mod.clear();
        this.owner = null;
    }
}

