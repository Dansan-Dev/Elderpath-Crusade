package io.github.elderpath_crusade.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdentityTest {

    @Test
    void localProvider_usesTheSavedName_andIsEditable() {
        LocalIdentityProvider provider = new LocalIdentityProvider(() -> "  Yxaria ");
        PlayerIdentity identity = provider.current();
        assertEquals("Yxaria", identity.displayName());
        assertEquals(PlayerIdentity.Provider.LOCAL, identity.provider());
        assertNull(identity.accountId());
        assertTrue(identity.isLocal());
        assertTrue(provider.nameEditable());
    }

    @Test
    void localProvider_withoutASavedName_isCalledPlayer() {
        assertEquals("Player", new LocalIdentityProvider(() -> null).current().displayName());
        assertEquals("Player", new LocalIdentityProvider(() -> "   ").current().displayName());
    }

    @Test
    void localProvider_followsLaterChangesToTheSavedName() {
        String[] name = {"One"};
        LocalIdentityProvider provider = new LocalIdentityProvider(() -> name[0]);
        assertEquals("One", provider.current().displayName());
        name[0] = "Two";
        assertEquals("Two", provider.current().displayName());
    }

    @Test
    void steamIdentity_needsAnAccountId() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerIdentity(PlayerIdentity.Provider.STEAM, null, "Someone"));
        PlayerIdentity steam = new PlayerIdentity(PlayerIdentity.Provider.STEAM, "76561198000000000", "Someone");
        assertFalse(steam.isLocal());
    }

    @Test
    void identity_needsAName() {
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerIdentity(PlayerIdentity.Provider.LOCAL, null, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerIdentity(null, null, "x"));
    }

    @Test
    void aProviderCanOwnTheName() {
        PlayerIdentityProvider platformOwned = new PlayerIdentityProvider() {
            @Override public PlayerIdentity current() {
                return new PlayerIdentity(PlayerIdentity.Provider.STEAM, "1", "Steam Name");
            }
            @Override public boolean nameEditable() { return false; }
        };
        assertFalse(platformOwned.nameEditable());
        assertEquals("Steam Name", platformOwned.current().displayName());
    }
}
