package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.cards.*;
import io.github.elderpath_crusade.managers.DeckManager;

import java.util.*;
import java.util.function.Function;

public final class CardFactory {
    private static final Map<String, Function<DeckManager.CardCreationParams, Card>> REGISTRY = new LinkedHashMap<>();

    private CardFactory() {}

    public static void initialize() {
        REGISTRY.clear();
        register("Wolf", p -> new WolfCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Wolf Cub", p -> new WolfCubCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Rogue", p -> new RogueCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Fairy", p -> new FairyCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Wind Spirit", p -> new WindSpiritCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Big Toad", p -> new BigToadCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Sniper", p -> new SniperCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Barbarian", p -> new BarbarianCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("King", p -> new KingCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Charger", p -> new ChargerCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Crossbowman", p -> new CrossbowmanCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Skeleton Bomber", p -> new SkeletonBomberCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Warp Mage", p -> new WarpMageCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Commander", p -> new CommanderCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Hero", p -> new HeroCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Storm Mage", p -> new StormMageCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Rifleman", p -> new RiflemanCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Crow", p -> new CrowCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Shockling", p -> new ShocklingCard(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
        register("Fireball", p -> new Fireball(p.board(), p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z()));
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
