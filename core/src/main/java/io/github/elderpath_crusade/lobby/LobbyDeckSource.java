package io.github.elderpath_crusade.lobby;

/**
 * How the players of a lobby build their decks (ONLINE-001): a rule the host picks when creating the lobby and every
 * joiner follows. The server only stores and shows it — a collection deck is a local file on the player's machine, so
 * it can't tell the two sources apart (it checks only that every card name is playable); the rule is kept by the
 * clients' own screens.
 */
public enum LobbyDeckSource {
    /** Each player drafts a deck from scratch (the starter cards plus four picks). */
    DRAFT("Draft"),
    /** Each player brings a deck from their own collection of won runs. */
    COLLECTION("Collection");

    private final String label;

    LobbyDeckSource(String label) {
        this.label = label;
    }

    /** Short name for screens. */
    public String label() {
        return label;
    }
}
