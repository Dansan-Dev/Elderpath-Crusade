package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.data.SpellDefinition;
import io.github.elderpath_crusade.data.SpellRegistry;
import io.github.elderpath_crusade.game.DeckManager;

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

        SpellRegistry.load();
        for (String spellName : SpellRegistry.getAllNames()) {
            registerSpell(spellName, SpellRegistry.get(spellName));
        }
    }

    private static void registerSummon(String displayName, String registryKey) {
        register(displayName, p -> new SummonCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z(), displayName, registryKey));
    }

    private static void registerSpell(String name, SpellDefinition definition) {
        register(name, p -> new SpellCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z(),
                name, definition));
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
