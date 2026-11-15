package io.github.elderpath_crusade.match;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ElderPieceIdsTest {
    @Test
    void newId_isRecognizedAndUnique() {
        String a = ElderPieceIds.newId();
        String b = ElderPieceIds.newId();
        assertTrue(ElderPieceIds.isElder(a));
        assertNotEquals(a, b);
    }

    @Test
    void ordinaryAndRelicCarrierIds_areNotElders() {
        assertFalse(ElderPieceIds.isElder(UUID.randomUUID().toString()));
        assertFalse(ElderPieceIds.isElder(RelicCarrierIds.idFor(io.github.elderpath_crusade.enums.PieceAlignment.P1)));
        assertFalse(ElderPieceIds.isElder(null));
    }
}
