package io.github.elderpath_crusade.abilities.data;

/**
 * Evaluates a Condition against an ExpressionContext.
 */
public class ConditionEvaluator {

    public static boolean evaluate(Condition condition, ExpressionContext context) {
        return switch (condition.type()) {
            case "Always" -> true;
            case "HealthBelow" -> {
                int threshold = ExpressionEvaluator.evaluateInt(condition.params().get("value"), context);
                int health = ExpressionEvaluator.evaluateInt(context.get(resolveTarget(condition) + ".health"), context);
                yield health < threshold;
            }
            case "HealthEquals" -> {
                int value = ExpressionEvaluator.evaluateInt(condition.params().get("value"), context);
                int health = ExpressionEvaluator.evaluateInt(context.get(resolveTarget(condition) + ".health"), context);
                yield health == value;
            }
            case "IsEnemy" -> {
                Object selfAlign = context.get("$self.alignment");
                Object targetAlign = context.get(resolveTarget(condition) + ".alignment");
                yield selfAlign != null && targetAlign != null && !selfAlign.equals(targetAlign);
            }
            case "IsFriendly" -> {
                Object selfAlign = context.get("$self.alignment");
                Object targetAlign = context.get(resolveTarget(condition) + ".alignment");
                yield selfAlign != null && selfAlign.equals(targetAlign);
            }
            case "ModuloEquals" -> {
                int value = ExpressionEvaluator.evaluateInt(condition.params().get("value"), context);
                int mod = ExpressionEvaluator.evaluateInt(condition.params().get("mod"), context);
                int equals = ExpressionEvaluator.evaluateInt(condition.params().get("equals"), context);
                yield mod != 0 && (value % mod) == equals;
            }
            default -> true;
        };
    }

    private static String resolveTarget(Condition condition) {
        Object target = condition.params().get("target");
        if (target instanceof String s) return s;
        return "$self";
    }
}
