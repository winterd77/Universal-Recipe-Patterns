package org.gtlcore.universalpatterns.integration.ae2.handler;

import org.gtlcore.gtlcore.integration.ae2.handler.MERecipePatternHelper;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/** A bounded boolean expression shared by material search and saved pattern rules. */
public final class MaterialExpression<T> {
    public static final int MAX_LENGTH = 1024;
    private final String input;
    private final Function<String, Predicate<T>> attributes;
    private int index;

    private MaterialExpression(String input, Function<String, Predicate<T>> attributes) {
        this.input = input;
        this.attributes = attributes;
    }

    public static <T> Predicate<T> parse(String input, Function<String, Predicate<T>> attributes) {
        if (input.length() > MAX_LENGTH) throw new IllegalArgumentException("too long");
        if (input.isBlank()) return value -> false;
        var parser = new MaterialExpression<>(input.toLowerCase(Locale.ROOT), attributes);
        var result = parser.or(0);
        parser.skipSpace();
        if (parser.index != input.length()) throw parser.error();
        return result;
    }

    private Predicate<T> or(int depth) {
        var result = and(depth);
        while (take('|')) {
            if (index < input.length() && input.charAt(index) == '|') index++;
            result = result.or(and(depth));
        }
        return result;
    }

    private Predicate<T> and(int depth) {
        var result = value(depth);
        while (take('&')) {
            if (index < input.length() && input.charAt(index) == '&') index++;
            result = result.and(value(depth));
        }
        return result;
    }

    private Predicate<T> value(int depth) {
        if (depth > 24) throw error();
        if (take('!')) return value(depth + 1).negate();
        if (take('(')) {
            var grouped = or(depth + 1);
            if (!take(')')) throw error();
            return grouped;
        }
        skipSpace();
        int start = index;
        while (index < input.length() && !Character.isWhitespace(input.charAt(index)) &&
                "&|!()".indexOf(input.charAt(index)) < 0) index++;
        if (start == index) throw error();
        String token = input.substring(start, index);
        var condition = attributes.apply(token);
        if (condition == null) throw new IllegalArgumentException(token);
        return condition;
    }

    private boolean take(char expected) {
        skipSpace();
        if (index >= input.length() || input.charAt(index) != expected) return false;
        index++;
        return true;
    }

    private void skipSpace() {
        while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++;
    }

    private IllegalArgumentException error() {
        return new IllegalArgumentException(Integer.toString(index + 1));
    }
}
