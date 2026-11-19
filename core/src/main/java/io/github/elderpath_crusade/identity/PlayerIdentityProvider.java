package io.github.elderpath_crusade.identity;

/**
 * Where the local player's identity comes from. Every screen asks this instead of reading a typed name:
 * today a {@link LocalIdentityProvider} (the name typed on the Play Online screen); in the Steam build a Steam
 * provider (profile name + SteamID, name not editable in the game). Keep Steam's classes out of the code that
 * uses this — a Steam provider is installed once at startup via {@code GameContext.setPlayerIdentityProvider}
 * and falls back to the local one when Steam is not running.
 */
public interface PlayerIdentityProvider {
    /** The identity to play under right now (a Steam profile name can change while the game runs). */
    PlayerIdentity current();

    /** Whether the player may type a different name in the game. False when the platform owns the name. */
    boolean nameEditable();
}
