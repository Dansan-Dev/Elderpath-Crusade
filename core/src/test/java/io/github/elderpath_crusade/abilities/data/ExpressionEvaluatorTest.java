package io.github.elderpath_crusade.abilities.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExpressionEvaluatorTest {

    private ExpressionContext context;

    @BeforeEach
    void setUp() {
        context = new ExpressionContext();
    }

    @Test
    void evaluateInt_literalInteger_returnsValue() {
        assertEquals(5, ExpressionEvaluator.evaluateInt(5, context));
    }

    @Test
    void evaluateInt_literalStringNumber_returnsValue() {
        assertEquals(3, ExpressionEvaluator.evaluateInt("3", context));
    }

    @Test
    void evaluateInt_variableReference_resolvesFromContext() {
        context.withState(Map.of("amount", 7));
        assertEquals(7, ExpressionEvaluator.evaluateInt("$state.amount", context));
    }

    @Test
    void evaluateInt_missingVariable_returnsZero() {
        assertEquals(0, ExpressionEvaluator.evaluateInt("$state.missing", context));
    }

    @Test
    void evaluateInt_addition_evaluatesBothSides() {
        context.withState(Map.of("amount", 3));
        assertEquals(4, ExpressionEvaluator.evaluateInt("$state.amount + 1", context));
    }

    @Test
    void evaluateInt_subtraction_evaluatesBothSides() {
        context.withState(Map.of("amount", 5));
        assertEquals(3, ExpressionEvaluator.evaluateInt("$state.amount - 2", context));
    }

    @Test
    void evaluateInt_multiplication_evaluatesBothSides() {
        context.withSelf(Map.of("damage", 4));
        assertEquals(8, ExpressionEvaluator.evaluateInt("$self.damage * 2", context));
    }

    @Test
    void evaluateInt_minFunction_returnsSmaller() {
        context.withState(Map.of("growth", 8));
        assertEquals(5, ExpressionEvaluator.evaluateInt("min($state.growth, 5)", context));
    }

    @Test
    void evaluateInt_maxFunction_returnsLarger() {
        context.withState(Map.of("x", 1));
        assertEquals(3, ExpressionEvaluator.evaluateInt("max($state.x, 3)", context));
    }

    @Test
    void evaluateInt_countFunction_returnsCollectionSize() {
        context.set("$self.adjacentAllies", List.of("a", "b", "c"));
        assertEquals(3, ExpressionEvaluator.evaluateInt("count($self.adjacentAllies)", context));
    }

    @Test
    void evaluateBoolean_trueValue_returnsTrue() {
        assertTrue(ExpressionEvaluator.evaluateBoolean(true, context));
    }

    @Test
    void evaluateBoolean_nonZeroInt_returnsTrue() {
        assertTrue(ExpressionEvaluator.evaluateBoolean(1, context));
    }

    @Test
    void evaluateBoolean_zero_returnsFalse() {
        assertFalse(ExpressionEvaluator.evaluateBoolean(0, context));
    }

    @Test
    void evaluate_nullExpression_returnsZero() {
        assertEquals(0, ExpressionEvaluator.evaluate(null, context));
    }
}
