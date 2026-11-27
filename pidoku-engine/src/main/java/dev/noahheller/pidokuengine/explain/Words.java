package dev.noahheller.pidokuengine.explain;

import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.strategy.Unit;

import java.util.List;
import java.util.function.Function;

/** Wording shared by the explanations: units, digits and counts as a player would say them. */
final class Words {

    private static final String[] NUMBERS = {"no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};

    private Words() {
    }

    /** "row 4", "column 3", "the top-left box". 1-based, as players count. */
    static String unit(Unit unit, PidokuRules rules) {
        return switch (unit.type()) {
            case ROW -> "row " + (unit.index() + 1);
            case COLUMN -> "column " + (unit.index() + 1);
            case BOX -> "the " + boxName(unit.index(), rules) + " box";
        };
    }

    /** "the row", "the column" or "the box". */
    static String unitKind(Unit unit) {
        return "the " + kind(unit);
    }

    static String kind(Unit unit) {
        return switch (unit.type()) {
            case ROW -> "row";
            case COLUMN -> "column";
            case BOX -> "box";
        };
    }

    /** "top-left", "centre" and so on for 4×4 and 9×9 boards; "number 7" on bigger ones. */
    private static String boxName(int index, PidokuRules rules) {
        int perRow = rules.boxesPerRow();
        int row = index / perRow;
        int col = index % perRow;
        if (perRow == 2) {
            return (row == 0 ? "top" : "bottom") + "-" + (col == 0 ? "left" : "right");
        }
        if (perRow == 3) {
            String vertical = new String[]{"top", "middle", "bottom"}[row];
            String horizontal = new String[]{"left", "middle", "right"}[col];
            if (row == 1 && col == 1) return "centre";
            return vertical + "-" + horizontal;
        }
        return "number " + (index + 1);
    }

    /** "a 3" or "an 8". */
    static String a(int digit) {
        return article(digit) + digit;
    }

    /** "a " or "an ", for the digit that follows. */
    static String article(int digit) {
        return digit == 8 ? "an " : "a ";
    }

    /** "one 3", "two 5s". */
    static String copies(int digit, int count) {
        return number(count) + " " + digit + (count == 1 ? "" : "s");
    }

    static String number(int n) {
        return n >= 0 && n < NUMBERS.length ? NUMBERS[n] : String.valueOf(n);
    }

    /** "2 and 7", "2, 7 and 9". */
    static <T> String and(List<T> items, Function<T, String> format) {
        return join(items, format, "and");
    }

    /** "2 or 7", "a 2 or a 7". */
    static <T> String or(List<T> items, Function<T, String> format) {
        return join(items, format, "or");
    }

    private static <T> String join(List<T> items, Function<T, String> format, String last) {
        List<String> words = items.stream().map(format).toList();
        if (words.size() <= 1) return String.join("", words);
        return String.join(", ", words.subList(0, words.size() - 1)) + " " + last + " " + words.getLast();
    }

    static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
