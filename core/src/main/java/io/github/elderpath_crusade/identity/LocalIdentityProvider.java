package io.github.elderpath_crusade.identity;

import java.util.function.Supplier;

/** The pre-Steam identity: whatever name the player typed (remembered in settings), "Player" before any. */
public final class LocalIdentityProvider implements PlayerIdentityProvider {
    public static final String DEFAULT_NAME = "Player";

    private final Supplier<String> savedName;

    public LocalIdentityProvider(Supplier<String> savedName) {
        if (savedName == null) throw new IllegalArgumentException("savedName required");
        this.savedName = savedName;
    }

    @Override
    public PlayerIdentity current() {
        String name = savedName.get();
        String shown = name == null || name.isBlank() ? DEFAULT_NAME : name.strip();
        return new PlayerIdentity(PlayerIdentity.Provider.LOCAL, null, shown);
    }

    @Override
    public boolean nameEditable() {
        return true;
    }
}
