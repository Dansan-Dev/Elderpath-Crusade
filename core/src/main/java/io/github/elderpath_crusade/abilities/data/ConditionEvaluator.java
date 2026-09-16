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
            case "Compare" -> {
                Object left = ExpressionEvaluator.evaluate(condition.params().get("left"), context);
                Object right = ExpressionEvaluator.evaluate(condition.params().get("right"), context);
                String op = String.valueOf(condition.params().get("op"));
                yield compare(left, right, op);
            }
            case "IsStunned" -> Boolean.TRUE.equals(context.get(resolveTarget(condition) + ".stunned"));
            default -> true;
        };
    }

    /**
     * General-purpose numeric/equality comparison for Branch/Recast conditions and
     * reaction conditions alike — e.g. "$drawn.cost <= 1" or "$lastDamage.targetDied == true".
     */
    private static boolean compare(Object left, Object right, String op) {
        if (left instanceof Number || right instanceof Number) {
            double l = toDouble(left);
            double r = toDouble(right);
            return switch (op) {
                case "==" -> l == r;
                case "!=" -> l != r;
                case "<" -> l < r;
                case "<=" -> l <= r;
                case ">" -> l > r;
                case ">=" -> l >= r;
                default -> false;
            };
        }
        boolean equal = java.util.Objects.equals(left, right)
                || String.valueOf(left).equals(String.valueOf(right));
        return switch (op) {
            case "==" -> equal;
            case "!=" -> !equal;
            default -> false;
        };
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof Boolean b) return b ? 1 : 0;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String resolveTarget(Condition condition) {
        Object target = condition.params().get("target");
        if (target instanceof String s) return s;
        return "$self";
    }
}
