package io.github.elderpath_crusade.client;

/**
 * What the result screen of an online match says: the winner's name as the headline and, for the loser, a second
 * line. "Name wins!" when you won; "OpponentName wins!" over "You lose" when you didn't. Pure text so it can be
 * tested; names are expected to be display-safe already (see utils.DisplayName).
 */
public record MatchResultText(String headline, String subline) {
    static final String OWN_FALLBACK = "You";
    static final String OPPONENT_FALLBACK = "Your opponent";

    /** subline is null when there is none. */
    public static MatchResultText online(String ownName, String opponentName, boolean youWon) {
        if (youWon) {
            return new MatchResultText(or(ownName, OWN_FALLBACK) + " wins!", null);
        }
        return new MatchResultText(or(opponentName, OPPONENT_FALLBACK) + " wins!", "You lose");
    }

    private static String or(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name;
    }
}
