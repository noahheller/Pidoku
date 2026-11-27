package dev.noahheller.pidokuengine.explain;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;
import dev.noahheller.pidokuengine.strategy.Move;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.Unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static dev.noahheller.pidokuengine.explain.Words.a;
import static dev.noahheller.pidokuengine.explain.Words.and;
import static dev.noahheller.pidokuengine.explain.Words.article;
import static dev.noahheller.pidokuengine.explain.Words.capitalize;
import static dev.noahheller.pidokuengine.explain.Words.copies;
import static dev.noahheller.pidokuengine.explain.Words.kind;
import static dev.noahheller.pidokuengine.explain.Words.number;
import static dev.noahheller.pidokuengine.explain.Words.or;
import static dev.noahheller.pidokuengine.explain.Words.unit;
import static dev.noahheller.pidokuengine.explain.Words.unitKind;

/**
 * Turns a solving step into something a player can follow: the cells to look at and one plain
 * sentence. Used by the tutorial (exported ahead of time, see {@link TutorialExport}) and hints.
 *
 * <p>The wording follows the Pidoku rule that a unit holds some digits more than once (two 1s and
 * two 5s on a 9×9 board): a digit is only "used up" in a unit once it holds as many copies as π
 * allows, and a single can be the second copy of a digit or two spots for both copies at once.
 */
public final class MoveExplainer {

    private MoveExplainer() {
    }

    /**
     * Explains a move as of {@code before}, the board just before the move is made.
     *
     * @throws IllegalArgumentException if the move carries no reason (every engine strategy gives one)
     */
    public static Explanation explain(StrategyEnum strategy, Move move, SolverSearchState before) {
        Move.Reason reason = move.getReason();
        if (reason == null) {
            throw new IllegalArgumentException("A " + strategy + " move has no reason to explain: " + move);
        }
        PidokuRules rules = before.getRules();
        return switch (strategy) {
            case HIDDEN_SINGLE_BOX, HIDDEN_SINGLE_LINE -> hiddenSingle(strategy, move, reason, before, rules);
            case NAKED_SINGLE -> nakedSingle(move, reason, before, rules);
            case NAKED_PAIR, NAKED_TRIPLE, NAKED_QUAD -> elimination(strategy, move, reason, nakedSubset(reason, before, rules));
            case HIDDEN_PAIR, HIDDEN_TRIPLE, HIDDEN_QUAD -> elimination(strategy, move, reason, hiddenSubset(reason, before, rules));
            case INTERSECTION_REMOVAL -> elimination(strategy, move, reason, intersection(reason, before, rules));
            case X_WING -> elimination(strategy, move, reason, xWing(reason, rules));
            case W_WING -> elimination(strategy, move, reason, wWing(reason, rules));
        };
    }

    // --- Singles ----------------------------------------------------------------------------

    private static Explanation hiddenSingle(StrategyEnum strategy, Move move, Move.Reason reason,
                                            SolverSearchState state, PidokuRules rules) {
        Unit unit = reason.units().getFirst();
        int digit = move.getValue();
        int frequency = rules.getDigitFrequency(digit);
        int needed = SearchUtils.remaining(state, unit, digit);
        String name = capitalize(unit(unit, rules));

        String text;
        if (needed == 1 && frequency == 1) {
            text = name + " needs " + a(digit) + ", and this is the only cell in it that can take one.";
        } else if (needed == 1) {
            String placed = frequency == 2 ? "one " + digit : copies(digit, frequency - 1);
            text = name + " already has " + placed + " and needs its " + (frequency == 2 ? "second" : "last")
                    + ", and this is the only cell left in it that can take " + a(digit) + ".";
        } else {
            String still = needed == frequency && needed == 2 ? "both its " + digit + "s" : copies(digit, needed) + " more";
            text = name + " still needs " + still + ", and only these " + number(needed) + " cells can take "
                    + a(digit) + ", so each of them is " + a(digit) + ".";
        }

        // What rules the digit out of the unit's other empty cells: the copies already used up in
        // a row, column or box crossing each of them.
        List<BoardPosition> others = new ArrayList<>();
        for (BoardPosition cell : unit.cells(rules)) {
            if (state.isEmpty(cell) && !reason.cells().contains(cell)) others.add(cell);
        }
        List<BoardPosition> blockers = blockers(state, rules, others, digit, unit);
        return new Explanation(strategy, move.getPosition(), digit, List.of(), List.of(unit), reason.cells(), blockers, text);
    }

    private static Explanation nakedSingle(Move move, Move.Reason reason, SolverSearchState state, PidokuRules rules) {
        BoardPosition cell = move.getPosition();
        int digit = move.getValue();
        Set<BoardPosition> blockers = new LinkedHashSet<>();
        boolean allUsedUp = true;
        for (int other : rules.getUniqueDigits()) {
            if (other == digit) continue;
            List<BoardPosition> used = usedUpIn(state, rules, cell, other, null);
            if (used.isEmpty()) {
                allUsedUp = false;
            } else {
                blockers.addAll(used);
            }
        }
        String text;
        if (allUsedUp) {
            String repeated = repeatedDigitsNote(rules);
            text = "Every other digit is already used up in this cell's row, column or box" + repeated
                    + ", so it can only be " + a(digit) + ".";
        } else {
            text = "Every other digit has been ruled out for this cell, by its row, column and box or by earlier steps, "
                    + "so it can only be " + a(digit) + ".";
        }
        return new Explanation(StrategyEnum.NAKED_SINGLE, cell, digit, List.of(), reason.units(), List.of(cell),
                List.copyOf(blockers), text);
    }

    /** " (a 1 or 5 only once there are two)" for the digits π repeats, or nothing if none repeat. */
    private static String repeatedDigitsNote(PidokuRules rules) {
        int[] repeated = Arrays.stream(rules.getUniqueDigits()).filter(d -> rules.getDigitFrequency(d) > 1).sorted().toArray();
        if (repeated.length == 0) return "";
        boolean allTwice = Arrays.stream(repeated).allMatch(d -> rules.getDigitFrequency(d) == 2);
        if (!allTwice) return " (a repeated digit only once it's there as many times as π allows)";
        return " (" + article(repeated[0]) + or(Arrays.stream(repeated).boxed().toList(), String::valueOf) + " only once there are two)";
    }

    // --- Eliminations -----------------------------------------------------------------------

    private static Explanation elimination(StrategyEnum strategy, Move move, Move.Reason reason, String text) {
        return new Explanation(strategy, null, 0, move.getEliminations(), reason.units(), reason.cells(), List.of(), text);
    }

    private static String nakedSubset(Move.Reason reason, SolverSearchState state, PidokuRules rules) {
        Unit unit = reason.units().getFirst();
        List<Integer> digits = reason.digits();
        String needs = and(digits, d -> copies(d, SearchUtils.remaining(state, unit, d)));
        return "These " + number(reason.cells().size()) + " cells in " + unit(unit, rules) + " can only take "
                + or(digits, String::valueOf) + ", and " + unitKind(unit) + " needs " + needs
                + ", so those go here: no other cell in " + unit(unit, rules) + " can be " + or(digits, Words::a) + ".";
    }

    private static String hiddenSubset(Move.Reason reason, SolverSearchState state, PidokuRules rules) {
        Unit unit = reason.units().getFirst();
        String needs = and(reason.digits(), d -> copies(d, SearchUtils.remaining(state, unit, d)));
        return capitalize(unit(unit, rules)) + " still needs " + needs + ", and they can only go in these "
                + number(reason.cells().size()) + " cells, so those cells can't hold anything else.";
    }

    private static String intersection(Move.Reason reason, SolverSearchState state, PidokuRules rules) {
        Unit source = reason.units().get(0);
        Unit target = reason.units().get(1);
        int digit = reason.digits().getFirst();
        int needed = SearchUtils.remaining(state, source, digit);
        String goes = needed == 1 ? "it goes" : "they go";
        return "In " + unit(source, rules) + ", every cell that can take " + a(digit) + " is in " + unit(target, rules)
                + ". " + capitalize(unitKind(source)) + " needs " + copies(digit, needed) + " and so does " + unitKind(target)
                + ", so " + goes + " where they cross: no other cell in " + unit(target, rules) + " can be " + a(digit) + ".";
    }

    private static String xWing(Move.Reason reason, PidokuRules rules) {
        List<Unit> units = reason.units();
        int digit = reason.digits().getFirst();
        String bases = plural(units.get(0), units.get(1));
        String covers = plural(units.get(2), units.get(3));
        String baseKind = kind(units.get(0)) + "s";
        String coverKind = kind(units.get(2)) + "s";
        return "In " + bases + ", " + a(digit) + " can only go in " + covers + ". Each of those " + baseKind
                + " needs its " + digit + " in one of these " + coverKind + ", so between them they fill both "
                + coverKind + "' " + digit + "s: no other cell in " + covers + " can be " + a(digit) + ".";
    }

    private static String wWing(Move.Reason reason, PidokuRules rules) {
        Unit link = reason.units().getFirst();
        int linkDigit = reason.digits().get(0);
        int other = reason.digits().get(1);
        String where = unit(link, rules);
        return "These two cells can only be " + linkDigit + " or " + other + ". In " + where + ", " + a(linkDigit)
                + " can only go in two places, and each of them sees one of these cells. If both cells were "
                + linkDigit + "s, " + where + " would have nowhere left for its " + linkDigit + ", so at least one of them is "
                + a(other) + ": no cell that sees both can be " + a(other) + ".";
    }

    /** "rows 2 and 7", from two units of the same kind. */
    private static String plural(Unit first, Unit second) {
        return kind(first) + "s " + (first.index() + 1) + " and " + (second.index() + 1);
    }

    // --- Board facts ------------------------------------------------------------------------

    /**
     * The filled cells that rule {@code digit} out of each of {@code cells}: for each cell, the
     * copies in a row, column or box through it (other than {@code skip}) that already holds as
     * many as π allows. A unit already used for an earlier cell is preferred, so one row of 5s
     * that blocks three cells is shown once. Cells ruled out some other way (an earlier
     * elimination) add nothing.
     */
    private static List<BoardPosition> blockers(SolverSearchState state, PidokuRules rules, List<BoardPosition> cells,
                                                int digit, Unit skip) {
        Set<BoardPosition> chosen = new LinkedHashSet<>();
        for (BoardPosition cell : cells) {
            List<List<BoardPosition>> full = usedUpUnits(state, rules, cell, digit, skip);
            if (full.isEmpty() || full.stream().anyMatch(chosen::containsAll)) continue;
            chosen.addAll(full.getFirst());
        }
        return List.copyOf(chosen);
    }

    /**
     * The copies of {@code digit} in the first unit through {@code cell} (other than {@code skip})
     * that holds as many as π allows, or nothing if no unit through the cell is full of it.
     */
    private static List<BoardPosition> usedUpIn(SolverSearchState state, PidokuRules rules, BoardPosition cell,
                                                int digit, Unit skip) {
        List<List<BoardPosition>> full = usedUpUnits(state, rules, cell, digit, skip);
        return full.isEmpty() ? List.of() : full.getFirst();
    }

    /** For each unit through {@code cell} (other than {@code skip}) that's full of {@code digit}, its copies. */
    private static List<List<BoardPosition>> usedUpUnits(SolverSearchState state, PidokuRules rules, BoardPosition cell,
                                                         int digit, Unit skip) {
        List<List<BoardPosition>> full = new ArrayList<>();
        for (Unit unit : Unit.unitsOf(cell, rules)) {
            if (unit.equals(skip) || SearchUtils.remaining(state, unit, digit) > 0) continue;
            List<BoardPosition> copies = new ArrayList<>();
            for (BoardPosition p : unit.cells(rules)) {
                if (state.getValue(p) == digit) copies.add(p);
            }
            full.add(copies);
        }
        return full;
    }
}
