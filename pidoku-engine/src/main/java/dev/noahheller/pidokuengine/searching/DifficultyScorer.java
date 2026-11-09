package dev.noahheller.pidokuengine.searching;

import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.strategy.Move;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;

import java.util.List;

/**
 * Turns a solve into a difficulty score.
 *
 * <p>Each step's cost is the cost of the easiest move a person could make there:
 * <pre>
 *   stepCost = strategy cost × (1.5 if it places a 1 or 5, digits that appear twice)
 * </pre>
 *
 * <p>How narrow the puzzle is comes from the "dependency" metric in Pelánek,
 * <i>Difficulty Rating of Sudoku Puzzles: An Overview and Evaluation</i> (2014): the mean
 * number of cells that could be filled with a single, over the first
 * {@link #DEPENDENCY_STEPS} steps. Later steps are left out because near the end of a
 * solve almost every puzzle has plenty of options, so they say little about difficulty.
 * Steps that needed an elimination count as 0 options.
 *
 * <p>The puzzle score combines the hardest step, the hardest few steps, narrowness and length:
 * <pre>
 *   score = MAX_STEP_WEIGHT × max(stepCost)
 *         + Σ top TOP_STEPS stepCosts
 *         + DEPENDENCY_WEIGHT × max(0, reference − dependency)
 *         + PLACEMENT_WEIGHT × placements
 *   reference = min(DEPENDENCY_REFERENCE, empty cells at start / 2)
 * </pre>
 * The reference is lowered on nearly full boards because a puzzle with 16 empty cells
 * can't offer 15 options; without it, 65+ clue puzzles would look narrow. Any puzzle
 * with 30+ empty cells is compared against the full reference, as in the paper.
 *
 * <p>The weights are hand-picked starting points and should be fitted to real solve times.
 */
public final class DifficultyScorer {

    public static final double REPEATED_DIGIT_MULTIPLIER = 1.5;
    public static final double MAX_STEP_WEIGHT = 3.0;
    public static final int TOP_STEPS = 10;
    public static final double PLACEMENT_WEIGHT = 0.2;

    /** Pelánek found 20-30 works best and results barely change inside that range. */
    public static final int DEPENDENCY_STEPS = 25;
    /** About what a very open puzzle averages (Trivial is around 14); more options than this add nothing. */
    public static final double DEPENDENCY_REFERENCE = 15.0;
    public static final double DEPENDENCY_WEIGHT = 3.0;

    private DifficultyScorer() {
    }

    /** Cost of making one move. */
    public static double moveCost(StrategyEnum strategy, Move move, PidokuRules rules) {
        double cost = strategy.getCost();
        if (move.isPlacement() && rules.getDigitFrequency(move.getValue()) > 1) {
            cost *= REPEATED_DIGIT_MULTIPLIER;
        }
        return cost;
    }

    /** Mean options over the first {@link #DEPENDENCY_STEPS} steps (or all of them, if fewer). */
    public static double dependency(List<Integer> options) {
        int n = Math.min(DEPENDENCY_STEPS, options.size());
        if (n == 0) return 0;
        double sum = 0;
        for (int i = 0; i < n; i++) sum += options.get(i);
        return sum / n;
    }

    public static double dependencyReference(int initialEmptyCells) {
        return Math.min(DEPENDENCY_REFERENCE, initialEmptyCells / 2.0);
    }

    public static int score(List<Double> stepCosts, List<Integer> options, int initialEmptyCells, int placements) {
        if (stepCosts.isEmpty()) return 0;
        double[] sorted = stepCosts.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double max = sorted[sorted.length - 1];
        double top = 0;
        for (int i = sorted.length - 1; i >= Math.max(0, sorted.length - TOP_STEPS); i--) {
            top += sorted[i];
        }
        double narrowness = Math.max(0, dependencyReference(initialEmptyCells) - dependency(options));
        return (int) Math.round(MAX_STEP_WEIGHT * max + top
                + DEPENDENCY_WEIGHT * narrowness
                + PLACEMENT_WEIGHT * placements);
    }
}
