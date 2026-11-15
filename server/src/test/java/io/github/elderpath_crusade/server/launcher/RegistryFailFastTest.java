package io.github.elderpath_crusade.server.launcher;

import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data.RelicRegistry;
import io.github.elderpath_crusade.data.SpellRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/** A missing or malformed data file must stop startup with a clear error, not leave an empty registry (audit AUDIT-003). */
class RegistryFailFastTest {

    @TempDir
    File assets;

    @BeforeEach
    void setUp() {
        Gdx.files = new AssetFiles(assets);
    }

    @AfterEach
    void tearDown() {
        Gdx.files = null;
    }

    private void write(String name, String content) throws IOException {
        File file = new File(assets, "data/" + name);
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), content);
    }

    private static void assertFailsNaming(Runnable load, String fileName) {
        IllegalStateException e = assertThrows(IllegalStateException.class, load::run);
        assertTrue(e.getMessage().contains(fileName), e.getMessage());
    }

    @Test
    void missingFiles_failFast() {
        assertFailsNaming(PieceRegistry::load, "pieces.yaml");
        assertFailsNaming(AbilityRegistry::load, "abilities.yaml");
        assertFailsNaming(SpellRegistry::load, "spells.yaml");
        assertFailsNaming(RelicRegistry::load, "relics.yaml");
    }

    @Test
    void unparsableYaml_failsFast() throws IOException {
        write("pieces.yaml", "pieces: [unclosed");
        write("abilities.yaml", "abilities: [unclosed");
        write("spells.yaml", "spells: [unclosed");
        write("relics.yaml", "relics: [unclosed");
        assertFailsNaming(PieceRegistry::load, "pieces.yaml");
        assertFailsNaming(AbilityRegistry::load, "abilities.yaml");
        assertFailsNaming(SpellRegistry::load, "spells.yaml");
        assertFailsNaming(RelicRegistry::load, "relics.yaml");
    }

    @Test
    void missingTopLevelKey_failsFast() throws IOException {
        write("pieces.yaml", "other: {}");
        write("abilities.yaml", "other: {}");
        write("spells.yaml", "other: {}");
        write("relics.yaml", "other: {}");
        assertFailsNaming(PieceRegistry::load, "pieces.yaml");
        assertFailsNaming(AbilityRegistry::load, "abilities.yaml");
        assertFailsNaming(SpellRegistry::load, "spells.yaml");
        assertFailsNaming(RelicRegistry::load, "relics.yaml");
    }

    @Test
    void validMinimalFiles_stillLoad() throws IOException {
        write("pieces.yaml", "pieces:\n  Wolf: {cost: 1, health: 2, damage: 1, speed: 1, actions: 1}\n");
        write("abilities.yaml", "abilities: {}\n");
        write("spells.yaml", "spells: {}\n");
        write("relics.yaml", "relics: {}\n");
        assertDoesNotThrow(PieceRegistry::load);
        assertDoesNotThrow(AbilityRegistry::load);
        assertDoesNotThrow(SpellRegistry::load);
        assertDoesNotThrow(RelicRegistry::load);
        assertNotNull(PieceRegistry.get("Wolf"));
    }
}
