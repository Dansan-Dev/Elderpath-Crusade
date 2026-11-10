package io.github.elderpath_crusade.match;

/**
 * The reserved naming scheme for a run-modified card's per-match CardCatalog/PieceRegistry
 * entry (UPG-003) — mirrors {@code RelicCarrierIds}'s role for relic carriers. A
 * {@code RunCardInstance} with a non-empty {@code RunCardModification} can't play through the
 * base card's own catalog entry (its stats/abilities are shared with every unmodified copy), so
 * its deck slot uses a synthetic name instead: the base display name plus the run instance id
 * that owns the modification, unique to that one card copy. {@code MatchSetup.prepare} resolves
 * any synthetic name in a side's deck against that side's {@code MatchConfig.cardMods()} and
 * registers a one-off {@code PieceDefinition}/{@code CardCatalog.Summon} entry for it before
 * the match starts — everything downstream (hand, {@code CardRules.playSummon}, bots) keeps
 * looking cards up by name exactly as it already does.
 */
public final class SyntheticCardNames {
    private static final String SEPARATOR = "#";

    private SyntheticCardNames() {}

    /** The deck-slot name for one modified card copy, e.g. "Wolf#run-3". */
    public static String encode(String baseCardName, String instanceId) {
        if (baseCardName == null || baseCardName.isBlank()) throw new IllegalArgumentException("baseCardName required");
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId required");
        if (baseCardName.contains(SEPARATOR) || instanceId.contains(SEPARATOR)) {
            throw new IllegalArgumentException("card name/instance id can't contain '" + SEPARATOR + "': " + baseCardName + "/" + instanceId);
        }
        return baseCardName + SEPARATOR + instanceId;
    }

    public static boolean isSynthetic(String cardName) {
        return cardName != null && cardName.contains(SEPARATOR);
    }

    /** The base display name encoded into a synthetic card name, e.g. "Wolf" from "Wolf#run-3". */
    public static String baseCardName(String syntheticName) {
        return syntheticName.substring(0, syntheticName.indexOf(SEPARATOR));
    }

    /** The run instance id encoded into a synthetic card name, e.g. "run-3" from "Wolf#run-3". */
    public static String instanceId(String syntheticName) {
        return syntheticName.substring(syntheticName.indexOf(SEPARATOR) + SEPARATOR.length());
    }

    /**
     * {@code name} if it isn't synthetic, else just its base card name — what a player should
     * ever actually see (BUG-046): the raw synthetic form leaked straight through to an
     * on-board piece's name label and its hover-preview title, since both ultimately read
     * {@code IdentityComponent.name}/{@code CardCatalog.Summon.name()}, which (correctly, for
     * stat/ability resolution) hold the full synthetic key. Never use this for a lookup key —
     * only for text actually shown to the player.
     */
    public static String displayName(String name) {
        return isSynthetic(name) ? baseCardName(name) : name;
    }
}
