package io.github.elderpath_crusade.ecs.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpawnAnimationComponentTest {

    @Test
    void alpha_risesFromZeroToOneOverDurationThenStaysDone() {
        SpawnAnimationComponent anim = new SpawnAnimationComponent();

        anim.elapsed = 0f;
        assertEquals(0f, anim.alpha());
        assertFalse(anim.isDone());

        anim.elapsed = SpawnAnimationComponent.DURATION / 2f;
        assertEquals(0.5f, anim.alpha(), 1e-5f);

        anim.elapsed = SpawnAnimationComponent.DURATION;
        assertEquals(1f, anim.alpha());
        assertTrue(anim.isDone());

        anim.elapsed = SpawnAnimationComponent.DURATION * 10;
        assertEquals(1f, anim.alpha(), "never overshoots past full opacity");
    }
}
