package io.github.elderpath_crusade.ui_objects;

import io.github.elderpath_crusade.path_loaders.ImagePathBackgroundAndUI;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** ART-005 follow-up: each button keeps one of five stone-plate variants, picked from its label. */
class ButtonPlateVariantTest {

    @Test
    void sameLabel_alwaysSameVariant_andInRange() {
        int variant = Button.plateVariantFor("Settings");
        assertEquals(variant, Button.plateVariantFor("Settings"));
        assertTrue(variant >= 0 && variant < ImagePathBackgroundAndUI.BUTTON_PLATES.size());
        assertEquals(Button.plateVariantFor(null), Button.plateVariantFor(null));
    }

    @Test
    void typicalMenuLabels_spreadOverSeveralVariants() {
        Set<Integer> used = new HashSet<>();
        for (String label : List.of("Game Mode", "Collection", "Settings", "Exit", "Main Menu",
                "Draft", "Collection Deck", "Pass Turn", "Easy", "Normal", "Hard")) {
            used.add(Button.plateVariantFor(label));
        }
        assertTrue(used.size() >= 3, "labels all landed on " + used);
    }
}
