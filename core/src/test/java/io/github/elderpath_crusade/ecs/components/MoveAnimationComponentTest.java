package io.github.elderpath_crusade.ecs.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure tween math — no engine/GameContext needed. */
class MoveAnimationComponentTest {

    @Test
    void linear_progressesFromZeroToOneOverDuration() {
        MoveAnimationComponent anim = new MoveAnimationComponent();
        anim.set(0, 0, 4, 2, false);

        anim.elapsed = 0f;
        assertEquals(0f, anim.shapedProgress());

        anim.elapsed = MoveAnimationComponent.DURATION / 2f;
        assertEquals(0.5f, anim.shapedProgress(), 1e-5f);

        anim.elapsed = MoveAnimationComponent.DURATION;
        assertEquals(1f, anim.shapedProgress(), 1e-5f);
        assertTrue(anim.isDone());
    }

    @Test
    void linear_neverOvershootsPastOneEvenWithExtraElapsed() {
        MoveAnimationComponent anim = new MoveAnimationComponent();
        anim.set(0, 0, 1, 1, false);
        anim.elapsed = MoveAnimationComponent.DURATION * 10;
        assertEquals(1f, anim.shapedProgress());
    }

    @Test
    void pingPong_risesToOneAtTheMidpointThenReturnsToZero() {
        MoveAnimationComponent anim = new MoveAnimationComponent();
        anim.set(2, 2, 3, 3, true);

        anim.elapsed = 0f;
        assertEquals(0f, anim.shapedProgress(), 1e-5f, "starts at \"from\"");

        anim.elapsed = MoveAnimationComponent.DURATION / 2f;
        assertEquals(1f, anim.shapedProgress(), 1e-5f, "peaks at \"to\" at the midpoint");

        anim.elapsed = MoveAnimationComponent.DURATION;
        assertEquals(0f, anim.shapedProgress(), 1e-5f, "returns to \"from\" by the end");
    }

    @Test
    void isDone_falseBeforeDurationElapsesAndTrueAtOrAfter() {
        MoveAnimationComponent anim = new MoveAnimationComponent();
        anim.set(0, 0, 1, 0, false);

        anim.elapsed = MoveAnimationComponent.DURATION - 0.01f;
        assertFalse(anim.isDone());

        anim.elapsed = MoveAnimationComponent.DURATION;
        assertTrue(anim.isDone());
    }
}
