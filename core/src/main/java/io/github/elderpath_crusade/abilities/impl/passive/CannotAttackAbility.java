package io.github.elderpath_crusade.abilities.impl.passive;

import io.github.elderpath_crusade.abilities.AbilityType;
import io.github.elderpath_crusade.abilities.PassiveAbility;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

/**
 * Passive ability that prevents the owner from attacking by setting attack range to -1.
 */
public class CannotAttackAbility implements PassiveAbility {
    private final StatsModifier mod;
    private MonsterGamePiece owner;

    public CannotAttackAbility() {
        this.mod = new StatsModifier();
        this.mod.source = this;
        this.mod.addRange = -1; // Negative range prevents attacks
    }

    @Override
    public String getName() { return "Cannot Attack"; }

    @Override
    public String getDescription() { return getAbilityDescription(); }

    public static String getAbilityDescription() { return "Cannot Attack"; }

    @Override
    public StatsModifier getModifier() { return mod; }

    @Override
    public boolean isConditionMet(MonsterGamePiece owner, io.github.elderpath_crusade.game_objects.board.Board board) {
        return true; // Always active
    }

    @Override
    public void onAttach(MonsterGamePiece owner) {
        this.owner = owner;
        owner.getStatsAccumulator().add(mod);
    }

    @Override
    public void onDetach() {
        if (owner != null) {
            owner.getStatsAccumulator().remove(mod);
        }
        this.owner = null;
    }
}

