package io.github.elderpath_crusade.game_objects.cards;

import io.github.elderpath_crusade.game.DeckManager;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.SyntheticCardNames;

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
            // BUG-046: a run-modified card's own registered name is its full synthetic key
            // (e.g. "Crow#run-6") — correct for the registryKey lookup right after it, wrong
            // for what the player should ever see as this card's title.
            card = new SummonCard(p.alignment(), p.x(), p.y(), p.width(), p.height(), p.z(),
                    SyntheticCardNames.displayName(s.name()), s.registryKey());
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
