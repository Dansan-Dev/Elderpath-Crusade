package io.github.elderpath_crusade.abilities.data;

import java.util.Collection;

public class ExpressionEvaluator {

    public static int evaluateInt(Object expression, ExpressionContext context) {
        Object result = evaluate(expression, context);
        if (result instanceof Number n) return n.intValue();
        return 0;
    }

    public static boolean evaluateBoolean(Object expression, ExpressionContext context) {
        Object result = evaluate(expression, context);
        if (result instanceof Boolean b) return b;
        if (result instanceof Number n) return n.intValue() != 0;
        return false;
    }

    public static Object evaluate(Object expression, ExpressionContext context) {
        if (expression == null) return 0;
        if (expression instanceof Number) return expression;
        if (expression instanceof Boolean) return expression;
        if (!(expression instanceof String expr)) return expression;

        expr = expr.trim();

        // Function calls: min(...), max(...), count(...)
        if (expr.startsWith("min(") && expr.endsWith(")")) {
            return evaluateMin(expr.substring(4, expr.length() - 1), context);
        }
        if (expr.startsWith("max(") && expr.endsWith(")")) {
            return evaluateMax(expr.substring(4, expr.length() - 1), context);
        }
        if (expr.startsWith("count(") && expr.endsWith(")")) {
            return evaluateCount(expr.substring(6, expr.length() - 1), context);
        }

        // Arithmetic: split on +, -, * (single operator, no precedence)
        int opIdx = findArithmeticOperator(expr);
        if (opIdx > 0) {
            String left = expr.substring(0, opIdx).trim();
            char op = expr.charAt(opIdx);
            String right = expr.substring(opIdx + 1).trim();
            int l = evaluateInt(left, context);
            int r = evaluateInt(right, context);
            return switch (op) {
                case '+' -> l + r;
                case '-' -> l - r;
                case '*' -> l * r;
                default -> 0;
            };
        }

        // Variable reference
        if (expr.startsWith("$")) {
            Object val = context.get(expr);
            return val != null ? val : 0;
        }

        // Literal number
        try { return Integer.parseInt(expr); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(expr); } catch (NumberFormatException ignored) {}

        return expr;
    }

    private static int findArithmeticOperator(String expr) {
        // Find rightmost + or -, or rightmost *, skipping the beginning (to handle $var)
        int parenDepth = 0;
        int lastAddSub = -1;
        int lastMul = -1;
        for (int i = 1; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (parenDepth == 0) {
                if ((c == '+' || c == '-') && i > 0) lastAddSub = i;
                else if (c == '*') lastMul = i;
            }
        }
        // Prefer +/- (lower precedence) over *
        return lastAddSub > 0 ? lastAddSub : lastMul;
    }

    private static Object evaluateMin(String args, ExpressionContext context) {
        String[] parts = splitArgs(args);
        if (parts.length < 2) return 0;
        return Math.min(evaluateInt(parts[0].trim(), context), evaluateInt(parts[1].trim(), context));
    }

    private static Object evaluateMax(String args, ExpressionContext context) {
        String[] parts = splitArgs(args);
        if (parts.length < 2) return 0;
        return Math.max(evaluateInt(parts[0].trim(), context), evaluateInt(parts[1].trim(), context));
    }

    private static Object evaluateCount(String arg, ExpressionContext context) {
        Object val = evaluate(arg.trim(), context);
        if (val instanceof Collection<?> c) return c.size();
        return 0;
    }

    private static String[] splitArgs(String args) {
        // Split on comma not inside parentheses
        int depth = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                return new String[]{args.substring(0, i), args.substring(i + 1)};
            }
        }
        return new String[]{args};
    }
}
