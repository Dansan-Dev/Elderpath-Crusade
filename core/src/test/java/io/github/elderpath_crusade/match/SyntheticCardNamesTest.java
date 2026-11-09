package io.github.elderpath_crusade.match;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyntheticCardNamesTest {

    @Test
    void encode_decode_roundTrips() {
        String name = SyntheticCardNames.encode("Wolf", "run-3");

        assertTrue(SyntheticCardNames.isSynthetic(name));
        assertEquals("Wolf", SyntheticCardNames.baseCardName(name));
        assertEquals("run-3", SyntheticCardNames.instanceId(name));
    }

    @Test
    void isSynthetic_falseForAPlainCardName() {
        assertFalse(SyntheticCardNames.isSynthetic("Wolf"));
    }

    @Test
    void isSynthetic_falseForNull() {
        assertFalse(SyntheticCardNames.isSynthetic(null));
    }

    @Test
    void encode_rejectsABaseNameContainingTheSeparator() {
        assertThrows(IllegalArgumentException.class, () -> SyntheticCardNames.encode("Wolf#Cub", "run-1"));
    }

    @Test
    void encode_rejectsAnInstanceIdContainingTheSeparator() {
        assertThrows(IllegalArgumentException.class, () -> SyntheticCardNames.encode("Wolf", "run#1"));
    }

    @Test
    void encode_rejectsBlankArguments() {
        assertThrows(IllegalArgumentException.class, () -> SyntheticCardNames.encode("", "run-1"));
        assertThrows(IllegalArgumentException.class, () -> SyntheticCardNames.encode("Wolf", ""));
    }
}
