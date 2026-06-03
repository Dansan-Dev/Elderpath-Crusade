package io.github.elderpath_crusade.data;

import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.abilities.impl._base_override.JumpMoveAbility;
import io.github.elderpath_crusade.abilities.impl._base_override.OncePerTurnAttackAbility;
import io.github.elderpath_crusade.abilities.impl._multi.aura.CommanderAuraAbility;
import io.github.elderpath_crusade.abilities.impl._multi.aura.KingEnemyAuraAbility;
import io.github.elderpath_crusade.abilities.impl._multi.aura.KingFriendlyAuraAbility;
import io.github.elderpath_crusade.abilities.impl._multi.aura.PackHunterAbility;
import io.github.elderpath_crusade.abilities.impl._multi.other.StormActionAbility;
import io.github.elderpath_crusade.abilities.impl.actionable.BombActionAbility;
import io.github.elderpath_crusade.abilities.impl.actionable.BoostActionAbility;
import io.github.elderpath_crusade.abilities.impl.actionable.DisplaceAbility;
import io.github.elderpath_crusade.abilities.impl.passive.CannotAttackAbility;
import io.github.elderpath_crusade.abilities.impl.passive.CrossbowmanRangeAbility;
import io.github.elderpath_crusade.abilities.impl.passive.RiflemanRangeAbility;
import io.github.elderpath_crusade.abilities.impl.passive.SniperRangeAbility;
import io.github.elderpath_crusade.abilities.impl.trigger.*;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Maps ability names (from pieces.yaml) to constructors.
 * Each ability may need the owning piece as a parameter.
 */
public final class AbilityFactory {

    private static final Map<String, Function<MonsterGamePiece, Ability>> REGISTRY = new HashMap<>();

    static {
        REGISTRY.put("PackHunter", p -> new PackHunterAbility());
        REGISTRY.put("CleaveAttack", p -> new CleaveAttackAbility());
        REGISTRY.put("JumpMove", p -> new JumpMoveAbility(p));
        REGISTRY.put("PushOnAttack", p -> new PushOnAttackAbility());
        REGISTRY.put("CommanderAura", p -> new CommanderAuraAbility());
        REGISTRY.put("CrossbowmanRange", p -> new CrossbowmanRangeAbility());
        REGISTRY.put("OncePerTurnAttack", p -> new OncePerTurnAttackAbility(p));
        REGISTRY.put("ExcessDamageCarryOver", p -> new ExcessDamageCarryOverAbility());
        REGISTRY.put("SwapOnAttack", p -> new SwapOnAttackAbility());
        REGISTRY.put("GrowthOnKill", p -> new GrowthOnKillAbility());
        REGISTRY.put("KingEnemyAura", p -> new KingEnemyAuraAbility());
        REGISTRY.put("KingFriendlyAura", p -> new KingFriendlyAuraAbility());
        REGISTRY.put("RiflemanRange", p -> new RiflemanRangeAbility());
        REGISTRY.put("RogueFreeStrike", p -> new RogueFreeStrikeAbility());
        REGISTRY.put("OnSummonShock", p -> new OnSummonShockAbility());
        REGISTRY.put("CannotAttack", p -> new CannotAttackAbility());
        REGISTRY.put("BombAction", p -> new BombActionAbility(p));
        REGISTRY.put("SniperRange", p -> new SniperRangeAbility());
        REGISTRY.put("StunSelfOnAttack", p -> new StunSelfOnAttackAbility());
        REGISTRY.put("StormAction", p -> new StormActionAbility(p));
        REGISTRY.put("Displace", p -> new DisplaceAbility(p));
        REGISTRY.put("BoostAction", p -> new BoostActionAbility(p));
    }

    /**
     * Create an ability instance by name.
     * @param name the ability name from pieces.yaml
     * @param owner the piece that will own this ability
     * @return the ability, or null if name is unknown
     */
    public static Ability create(String name, MonsterGamePiece owner) {
        Function<MonsterGamePiece, Ability> factory = REGISTRY.get(name);
        if (factory == null) return null;
        return factory.apply(owner);
    }
}
