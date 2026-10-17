package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data.SpellDefinition;
import io.github.elderpath_crusade.data.SpellRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Every playable card by display name: summons (mapped to their pieces.yaml registry key)
 * and spells (their spells.yaml SpellDefinition). The pure, headless source of truth for
 * "what is this card" — CardRules and bots read it; CardFactory builds renderable cards from it.
 */
public final class CardCatalog {
    public sealed interface Entry permits Summon, Spell {
        String name();
    }

    public record Summon(String name, String registryKey) implements Entry {}

    public record Spell(String name, SpellDefinition definition) implements Entry {}

    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final Set<String> NON_DRAFTABLE = Set.of("Wolf", "Wolf Cub");

    private CardCatalog() {}

    /** Registers every summon and every spell in SpellRegistry (loads it). Call after PieceRegistry.load(). */
    public static void initialize() {
        ENTRIES.clear();
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

    public static void registerSummon(String displayName, String registryKey) {
        ENTRIES.put(displayName, new Summon(displayName, registryKey));
    }

    public static void registerSpell(String name, SpellDefinition definition) {
        ENTRIES.put(name, new Spell(name, definition));
    }

    public static void clear() {
        ENTRIES.clear();
    }

    /** The catalog entry for this display name, or null if no such card is registered. */
    public static Entry get(String name) {
        return name == null ? null : ENTRIES.get(name);
    }

    /** The pieces.yaml registry key for a summon card's display name — null if it isn't a registered summon. */
    public static String getRegistryKeyForSummon(String name) {
        return get(name) instanceof Summon s ? s.registryKey() : null;
    }

    /** The summoned piece's definition, or null if name isn't a summon or its piece isn't loaded. */
    public static PieceDefinition getPieceDefinition(String name) {
        String key = getRegistryKeyForSummon(name);
        return key == null ? null : PieceRegistry.get(key);
    }

    /** Mana cost to play this card, or -1 if the card (or its piece definition) is unknown. */
    public static int getCost(String name) {
        Entry entry = get(name);
        if (entry instanceof Spell spell) return spell.definition().manaCost();
        if (entry instanceof Summon) {
            PieceDefinition def = getPieceDefinition(name);
            return def == null ? -1 : def.cost();
        }
        return -1;
    }

    public static Collection<String> getAllNames() {
        return Collections.unmodifiableSet(ENTRIES.keySet());
    }

    public static Collection<String> getDraftableNames() {
        return ENTRIES.keySet().stream().filter(n -> !NON_DRAFTABLE.contains(n)).toList();
    }
}
