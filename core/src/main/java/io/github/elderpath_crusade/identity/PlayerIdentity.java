package io.github.elderpath_crusade.identity;

/**
 * Who a player is, as far as the game knows. displayName is what other players see (never trusted to be
 * unique or clean — see utils.DisplayName); accountId is the stable account behind it: null for a
 * locally typed name, the 64-bit SteamID (as text) for a Steam player. Only accountId identifies; names change.
 */
public record PlayerIdentity(Provider provider, String accountId, String displayName) {
    public enum Provider { LOCAL, STEAM }

    public PlayerIdentity {
        if (provider == null) throw new IllegalArgumentException("provider required");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName required");
        if (provider != Provider.LOCAL && (accountId == null || accountId.isBlank())) {
            throw new IllegalArgumentException(provider + " identities need an accountId");
        }
    }

    public boolean isLocal() {
        return provider == Provider.LOCAL;
    }
}
