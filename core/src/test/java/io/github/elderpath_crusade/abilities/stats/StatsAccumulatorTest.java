package io.github.elderpath_crusade.abilities.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatsAccumulatorTest {
    private StatsAccumulator acc;

    @BeforeEach
    void setUp() {
        acc = new StatsAccumulator();
    }

    /** Applies the documented formula over every modifier on the accumulator (damage stat). */
    private int effectiveDamage(int base) {
        int adds = 0;
        float mults = 0;
        for (StatsModifier m : acc.getAll()) {
            adds += m.addDamage;
            mults += m.multDamage;
        }
        return StatsModifier.applyInt(base, adds, mults);
    }

    private static StatsModifier add(int n) {
        StatsModifier m = new StatsModifier();
        m.addDamage = n;
        return m;
    }

    private static StatsModifier mult(float f) {
        StatsModifier m = new StatsModifier();
        m.multDamage = f;
        return m;
    }

    @Test
    void noModifiers_returnsBase() {
        assertEquals(3, effectiveDamage(3));
    }

    @Test
    void addOnly() {
        acc.add(add(2));
        acc.add(add(1));
        assertEquals(6, effectiveDamage(3));
    }

    @Test
    void multOnly() {
        acc.add(mult(1f));
        assertEquals(6, effectiveDamage(3));
    }

    @Test
    void multsSumIndependently() {
        acc.add(mult(0.5f));
        acc.add(mult(0.5f));
        assertEquals(6, effectiveDamage(3));
    }

    @Test
    void combined_addsApplyBeforeMult() {
        acc.add(add(1));
        acc.add(mult(1f));
        assertEquals(8, effectiveDamage(3)); // (3+1)*(1+1)
    }

    @Test
    void negativeResult_clampsToZero() {
        acc.add(add(-10));
        assertEquals(0, effectiveDamage(3));
    }

    @Test
    void multMinusOne_zeroesStat() {
        acc.add(mult(-1f));
        assertEquals(0, effectiveDamage(5));
    }

    @Test
    void applyInt_rounds() {
        assertEquals(2, StatsModifier.applyInt(1, 0, 0.5f)); // 1.5 rounds up
        assertEquals(4, StatsModifier.applyInt(3, 0, 0.4f)); // 4.2 -> 4
    }

    @Test
    void addingSameModifierTwice_isNoOp() {
        StatsModifier m = add(2);
        acc.add(m);
        acc.add(m);
        assertEquals(1, acc.getAll().size());
        assertEquals(5, effectiveDamage(3));
    }

    @Test
    void noOpAndNullModifiers_areIgnored() {
        acc.add(new StatsModifier());
        acc.add(null);
        assertTrue(acc.getAll().isEmpty());
    }

    @Test
    void remove_detaches() {
        StatsModifier m = add(2);
        acc.add(m);
        assertTrue(acc.has(m));
        acc.remove(m);
        assertFalse(acc.has(m));
        assertEquals(3, effectiveDamage(3));
        assertDoesNotThrow(() -> acc.remove(m));
        assertDoesNotThrow(() -> acc.remove(null));
    }

    @Test
    void removeBySource_removesOnlyMatching() {
        Object source = new Object();
        StatsModifier a = add(1);
        a.source = source;
        StatsModifier b = add(1);
        acc.add(a);
        acc.add(b);
        acc.removeBySource(source);
        assertFalse(acc.has(a));
        assertTrue(acc.has(b));
    }

    @Test
    void clear_detachesFromAllAccumulators() {
        StatsAccumulator other = new StatsAccumulator();
        StatsModifier m = add(2);
        acc.add(m);
        other.add(m);
        m.clear();
        assertFalse(acc.has(m));
        assertFalse(other.has(m));
    }

    @Test
    void clear_onUnattachedModifier_isHarmless() {
        assertDoesNotThrow(() -> add(1).clear());
    }

    @Test
    void getAll_isUnmodifiable() {
        acc.add(add(1));
        assertThrows(UnsupportedOperationException.class, () -> acc.getAll().clear());
    }
}
