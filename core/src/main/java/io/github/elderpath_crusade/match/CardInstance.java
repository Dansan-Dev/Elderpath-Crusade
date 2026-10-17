package io.github.elderpath_crusade.match;

/**
 * One physical card in a match: a stable instanceId (unique within the match) plus the card's
 * display name as registered in CardCatalog (e.g. "Wolf Cub", "Fireball"). Two copies of the
 * same card have different instance ids, so hands can be mirrored and cards played by id
 * regardless of hand order.
 */
public record CardInstance(String instanceId, String cardName) {
    public CardInstance {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId required");
        if (cardName == null || cardName.isBlank()) throw new IllegalArgumentException("cardName required");
    }
}
