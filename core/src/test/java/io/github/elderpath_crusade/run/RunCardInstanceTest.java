package io.github.elderpath_crusade.run;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RunCardInstanceTest {

    @Test
    void unmodified_hasNoModificationAndIsNotModified() {
        RunCardInstance instance = RunCardInstance.unmodified("run-0", "Wolf");
        assertEquals(RunCardModification.NONE, instance.modification());
        assertFalse(instance.isModified());
    }

    @Test
    void withModification_returnsAModifiedCopyWithTheSameIdentity() {
        RunCardInstance base = RunCardInstance.unmodified("run-0", "Wolf");
        RunCardModification mod = new RunCardModification(Map.of("damage", 1), List.of());
        RunCardInstance modified = base.withModification(mod);

        assertEquals("run-0", modified.instanceId());
        assertEquals("Wolf", modified.cardName());
        assertTrue(modified.isModified());
        assertEquals(mod, modified.modification());
        // base instance itself is untouched (records are immutable)
        assertFalse(base.isModified());
    }

    @Test
    void nullModification_defaultsToNone() {
        RunCardInstance instance = new RunCardInstance("run-0", "Wolf", null);
        assertFalse(instance.isModified());
    }

    @Test
    void constructor_rejectsBlankInstanceId() {
        assertThrows(IllegalArgumentException.class, () -> RunCardInstance.unmodified("", "Wolf"));
        assertThrows(IllegalArgumentException.class, () -> RunCardInstance.unmodified(null, "Wolf"));
    }

    @Test
    void constructor_rejectsBlankCardName() {
        assertThrows(IllegalArgumentException.class, () -> RunCardInstance.unmodified("run-0", ""));
        assertThrows(IllegalArgumentException.class, () -> RunCardInstance.unmodified("run-0", null));
    }

    @Test
    void modification_isEmptyWhenBothStatDeltasAndAbilitiesAreEmpty() {
        assertTrue(RunCardModification.NONE.isEmpty());
        assertFalse(new RunCardModification(Map.of("damage", 1), List.of()).isEmpty());
        assertFalse(new RunCardModification(Map.of(), List.of("Frenzy")).isEmpty());
    }
}
