package io.github.elderpath_crusade.abilities.data;

import io.github.elderpath_crusade.enums.PieceAlignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {
    private ExpressionContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ExpressionContext();
        ctx.set("$self.alignment", PieceAlignment.P1);
        ctx.set("$self.health", 3);
        ctx.set("$self.stunned", false);
        ctx.set("$target.alignment", PieceAlignment.P2);
        ctx.set("$target.health", 5);
        ctx.set("$target.stunned", true);
    }

    private boolean eval(String type, Object... kv) {
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) params.put((String) kv[i], kv[i + 1]);
        return ConditionEvaluator.evaluate(new Condition(type, params), ctx);
    }

    @Test
    void always_isTrue() {
        assertTrue(eval("Always"));
    }

    @Test
    void healthBelow() {
        assertTrue(eval("HealthBelow", "value", 4));
        assertFalse(eval("HealthBelow", "value", 3));
    }

    @Test
    void healthBelow_withTarget() {
        assertTrue(eval("HealthBelow", "value", 6, "target", "$target"));
        assertFalse(eval("HealthBelow", "value", 5, "target", "$target"));
    }

    @Test
    void healthEquals() {
        assertTrue(eval("HealthEquals", "value", 3));
        assertFalse(eval("HealthEquals", "value", 4));
    }

    @Test
    void isEnemy() {
        assertTrue(eval("IsEnemy", "target", "$target"));
        assertFalse(eval("IsEnemy", "target", "$self"));
    }

    @Test
    void isEnemy_missingAlignment_isFalse() {
        assertFalse(eval("IsEnemy", "target", "$nobody"));
    }

    @Test
    void isFriendly() {
        assertTrue(eval("IsFriendly", "target", "$self"));
        assertFalse(eval("IsFriendly", "target", "$target"));
    }

    @Test
    void moduloEquals() {
        assertTrue(eval("ModuloEquals", "value", 7, "mod", 3, "equals", 1));
        assertFalse(eval("ModuloEquals", "value", 7, "mod", 3, "equals", 2));
    }

    @Test
    void moduloEquals_zeroModulus_isFalse() {
        assertFalse(eval("ModuloEquals", "value", 7, "mod", 0, "equals", 0));
    }

    @Test
    void compare_numeric() {
        assertTrue(eval("Compare", "left", 2, "right", 3, "op", "<"));
        assertFalse(eval("Compare", "left", 3, "right", 3, "op", "<"));
        assertTrue(eval("Compare", "left", 3, "right", 3, "op", "<="));
        assertTrue(eval("Compare", "left", 4, "right", 3, "op", ">"));
        assertTrue(eval("Compare", "left", 3, "right", 3, "op", ">="));
        assertTrue(eval("Compare", "left", 3, "right", 3, "op", "=="));
        assertTrue(eval("Compare", "left", 2, "right", 3, "op", "!="));
        assertFalse(eval("Compare", "left", 2, "right", 3, "op", "??"));
    }

    @Test
    void compare_usesContextVariables() {
        assertTrue(eval("Compare", "left", "$self.health", "right", 3, "op", "=="));
    }

    @Test
    void compare_nonNumeric_equalityOnly() {
        assertTrue(eval("Compare", "left", "abc", "right", "abc", "op", "=="));
        assertTrue(eval("Compare", "left", "abc", "right", "xyz", "op", "!="));
        assertFalse(eval("Compare", "left", "abc", "right", "xyz", "op", "<"));
    }

    @Test
    void isStunned() {
        assertTrue(eval("IsStunned", "target", "$target"));
        assertFalse(eval("IsStunned"));
    }

    @Test
    void unknownType_defaultsToTrue() {
        assertTrue(eval("NoSuchCondition"));
    }
}
