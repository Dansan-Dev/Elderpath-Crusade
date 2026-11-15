package io.github.elderpath_crusade.ecs.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeathFlashComponentTest {

    @Test
    void alpha_fadesFromOneToZeroOverDurationThenStaysDone() {
        DeathFlashComponent flash = new DeathFlashComponent();

        flash.elapsed = 0f;
        assertEquals(1f, flash.alpha());
        assertFalse(flash.isDone());

        flash.elapsed = DeathFlashComponent.DURATION / 2f;
        assertEquals(0.5f, flash.alpha(), 1e-5f);

        flash.elapsed = DeathFlashComponent.DURATION;
        assertEquals(0f, flash.alpha());
        assertTrue(flash.isDone());

        flash.elapsed = DeathFlashComponent.DURATION * 10;
        assertEquals(0f, flash.alpha(), "never goes negative past full transparency");
    }
}
