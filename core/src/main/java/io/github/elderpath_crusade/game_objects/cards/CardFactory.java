package io.github.elderpath_crusade.game_objects.cards;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ActionDef;
import io.github.elderpath_crusade.abilities.data.Cost;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.EntityUtils;
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

        registerSpell("Fireball",
                new AbilityDefinition("Fireball", "Deal 2 damage to a target piece.", null, null,
                        List.of(new ActionDef(List.of(new Cost("Mana", 3)), null,
                                List.of(new EffectNode("Damage", Map.of("amount", 2))))),
                        null),
                (board, plot, caster) -> board.getEntityAtPlot(plot) != null);

        registerSpell("Frostbolt",
                new AbilityDefinition("Frostbolt", "Deal 1 damage to an enemy\nand freeze it (0 actions).", null, null,
                        List.of(new ActionDef(List.of(new Cost("Mana", 2)), null,
                                List.of(
                                        new EffectNode("Damage", Map.of("amount", 1)),
                                        new EffectNode("SetActions", Map.of("amount", 0))))),
                        null),
                (board, plot, caster) -> {
                    Entity e = board.getEntityAtPlot(plot);
                    if (e == null) return false;
                    PieceAlignment target = EntityUtils.getAlignment(e);
                    return target != caster && target != PieceAlignment.NEUTRAL;
                });

        registerSpell("Healing Light",
                new AbilityDefinition("HealingLight", "Heal a friendly piece\nfor 2 HP.", null, null,
                        List.of(new ActionDef(List.of(new Cost("Mana", 2)), null,
                                List.of(new EffectNode("Heal", Map.of("amount", 2))))),
                        null),
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

    private static void registerSpell(String name, AbilityDefinition definition,
                                      SpellCard.SpellTargetFilter targetFilter) {
        register(name, p -> new SpellCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z(),
                name, definition, targetFilter));
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
