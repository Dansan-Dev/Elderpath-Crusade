package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.systems.CombatSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.DeckManager;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;

import java.util.*;
import java.util.function.Function;

public final class CardFactory {
    private static final Map<String, Function<DeckManager.CardCreationParams, Card>> REGISTRY = new LinkedHashMap<>();

    private CardFactory() {}

    public static void initialize() {
        REGISTRY.clear();
        registerSummon("Wolf", "Wolf");
        registerSummon("Wolf Cub", "WolfCub");
        registerSummon("Rogue", "Rogue");
        registerSummon("Fairy", "Fairy");
        registerSummon("Wind Spirit", "WindSpirit");
        registerSummon("Big Toad", "BigToad");
        registerSummon("Sniper", "Sniper");
        registerSummon("Barbarian", "Barbarian");
        registerSummon("King", "King");
        registerSummon("Charger", "Charger");
        registerSummon("Crossbowman", "Crossbowman");
        registerSummon("Skeleton Bomber", "SkeletonBomber");
        registerSummon("Warp Mage", "WarpMage");
        registerSummon("Commander", "Commander");
        registerSummon("Hero", "Hero");
        registerSummon("Storm Mage", "StormMage");
        registerSummon("Rifleman", "Rifleman");
        registerSummon("Crow", "Crow");
        registerSummon("Shockling", "Shockling");

        registerSpell("Fireball", 3, "Deal 2 damage to a target piece.",
                (board, plot, caster) -> {
                    Entity e = board.getEntityAtPlot(plot);
                    if (e != null) GameContext.get().getEcsEngine().getSystem(CombatSystem.class).applyDamage(e, 2);
                },
                (board, plot, caster) -> board.getEntityAtPlot(plot) != null);

        registerSpell("Frostbolt", 2, "Deal 1 damage to an enemy\nand freeze it (0 actions).",
                (board, plot, caster) -> {
                    Entity e = board.getEntityAtPlot(plot);
                    if (e != null) {
                        GameContext.get().getEcsEngine().getSystem(CombatSystem.class).applyDamage(e, 1);
                        StatsComponent stats = e.getComponent(StatsComponent.class);
                        if (stats != null && stats.currentHealth > 0) stats.remainingActions = 0;
                    }
                },
                (board, plot, caster) -> {
                    Entity e = board.getEntityAtPlot(plot);
                    if (e == null) return false;
                    PieceAlignment target = EntityUtils.getAlignment(e);
                    return target != caster && target != PieceAlignment.NEUTRAL;
                });

        registerSpell("Healing Light", 2, "Heal a friendly piece\nfor 2 HP.",
                (board, plot, caster) -> {
                    Entity e = board.getEntityAtPlot(plot);
                    if (e != null) {
                        StatsComponent stats = e.getComponent(StatsComponent.class);
                        if (stats != null) stats.currentHealth = Math.min(stats.currentHealth + 2, EntityUtils.getMaxHealth(e));
                    }
                },
                (board, plot, caster) -> {
                    Entity e = board.getEntityAtPlot(plot);
                    if (e == null) return false;
                    return EntityUtils.getAlignment(e) == caster
                            && EntityUtils.getCurrentHealth(e) < EntityUtils.getMaxHealth(e);
                });
    }

    private static void registerSummon(String displayName, String registryKey) {
        register(displayName, p -> new SummonCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z(), displayName, registryKey));
    }

    private static void registerSpell(String name, int manaCost, String description,
                                      SpellCard.SpellEffect effect, SpellCard.SpellTargetFilter targetFilter) {
        register(name, p -> new SpellCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z(),
                name, manaCost, description, effect, targetFilter));
    }

    public static void register(String name, Function<DeckManager.CardCreationParams, Card> creator) {
        REGISTRY.put(name, creator);
    }

    public static Card create(String name, DeckManager.CardCreationParams params) {
        Function<DeckManager.CardCreationParams, Card> creator = REGISTRY.get(name);
        if (creator == null) throw new IllegalArgumentException("Unknown card: " + name);
        return creator.apply(params);
    }

    public static Function<DeckManager.CardCreationParams, Card> getCreator(String name) {
        Function<DeckManager.CardCreationParams, Card> creator = REGISTRY.get(name);
        if (creator == null) throw new IllegalArgumentException("Unknown card: " + name);
        return creator;
    }

    public static Collection<String> getAllNames() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    public static Collection<String> getDraftableNames() {
        Set<String> excluded = Set.of("Wolf", "Wolf Cub");
        return REGISTRY.keySet().stream().filter(n -> !excluded.contains(n)).toList();
    }
}
