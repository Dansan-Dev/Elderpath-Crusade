package io.github.elderpath_crusade.abilities.data;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for "Compare" (general-purpose numeric/equality comparison, used
 * by Scavenge's "$drawn.cost <= 1" and Chain Lightning's "$lastDamage.targetDied == true")
 * and "IsStunned" (Blizzard's "already stunned" check), both used by Branch/Recast
 * conditions as well as ordinary reaction conditions.
 */
class ConditionEvaluatorCompareTest {

    private boolean compare(Object left, String op, Object right) {
        return ConditionEvaluator.evaluate(new Condition("Compare", Map.of("left", left, "op", op, "right", right)),
                new ExpressionContext());
    }

    @Test
    void numericOperators() {
        assertTrue(compare(1, "<=", 1));
        assertTrue(compare(0, "<=", 1));
        assertFalse(compare(2, "<=", 1));

        assertTrue(compare(2, ">", 1));
        assertFalse(compare(1, ">", 1));

        assertTrue(compare(1, ">=", 1));
        assertTrue(compare(1, "<", 2));
        assertTrue(compare(3, "==", 3));
        assertTrue(compare(3, "!=", 4));
    }

    @Test
    void booleanEquality() {
        assertTrue(compare(true, "==", true));
        assertFalse(compare(true, "==", false));
        assertTrue(compare(true, "!=", false));
    }

    @Test
    void stringEquality_forAlignmentNames() {
        assertTrue(compare("P1", "==", "P1"));
        assertTrue(compare("P1", "!=", "NEUTRAL"));
        assertFalse(compare("NEUTRAL", "!=", "NEUTRAL"));
    }

    @Test
    void contextExpressions_areEvaluatedBeforeComparing() {
        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$drawn.cost", 1);
        Condition cond = new Condition("Compare", Map.of("left", "$drawn.cost", "op", "<=", "right", 1));
        assertTrue(ConditionEvaluator.evaluate(cond, ctx));

        ctx.set("$drawn.cost", 2);
        assertFalse(ConditionEvaluator.evaluate(cond, ctx));
    }

    @Test
    void isStunned_defaultsToSelf_readsFromContext() {
        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$self.stunned", true);
        assertTrue(ConditionEvaluator.evaluate(new Condition("IsStunned", Map.of()), ctx));

        ctx.set("$self.stunned", false);
        assertFalse(ConditionEvaluator.evaluate(new Condition("IsStunned", Map.of()), ctx));
    }

    @Test
    void isStunned_explicitTarget_readsTargetPrefixedKey() {
        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$target.stunned", true);
        assertTrue(ConditionEvaluator.evaluate(new Condition("IsStunned", Map.of("target", "$target")), ctx));
    }
}
