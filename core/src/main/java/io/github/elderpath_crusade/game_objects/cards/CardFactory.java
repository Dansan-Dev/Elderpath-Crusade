package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.game.DeckManager;
import io.github.elderpath_crusade.match.CardCatalog;

import java.util.Collection;

/**
 * Builds renderable cards (views) for the cards registered in CardCatalog. The catalog is
 * the headless source of truth for what each card is; this class only adds rendering.
 */
public final class CardFactory {
    private CardFactory() {}

    public static void initialize() {
        CardCatalog.initialize();
    }

    /** The pieces.yaml registry key for a summon card's display name — null if displayName isn't a registered summon. */
    public static String getRegistryKeyForSummon(String displayName) {
        return CardCatalog.getRegistryKeyForSummon(displayName);
    }

    /** Creates a renderable card for a card name; instanceId is the match card it shows (null for a draft preview). */
    public static Card create(String name, String instanceId, DeckManager.CardCreationParams p) {
        CardCatalog.Entry entry = CardCatalog.get(name);
        Card card;
        if (entry instanceof CardCatalog.Summon s) {
            card = new SummonCard(p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z(), s.name(), s.registryKey());
        } else if (entry instanceof CardCatalog.Spell spell) {
            card = new SpellCard(p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z(), spell.name(), spell.definition());
        } else {
            throw new IllegalArgumentException("Unknown card: " + name);
        }
        card.setInstanceId(instanceId);
        return card;
    }

    public static Collection<String> getAllNames() {
        return CardCatalog.getAllNames();
    }

    public static Collection<String> getDraftableNames() {
        return CardCatalog.getDraftableNames();
    }
}
