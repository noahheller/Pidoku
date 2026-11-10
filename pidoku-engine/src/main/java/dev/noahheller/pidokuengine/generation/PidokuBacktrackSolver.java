package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Brute-force search: fills grids and counts solutions (for uniqueness checks).
 *
 * <p>Thread-safe: it holds only the rules. When a random fill is wanted, the caller passes the
 * random number generator in with the call, so one solver can be shared by any number of threads.
 */
public class PidokuBacktrackSolver implements PidokuSolver {

    /** Counting stops here by default: two solutions is enough to know a puzzle isn't unique. */
    public static final int UNIQUENESS_CUTOFF = 2;

    private final PidokuRules rules;

    public PidokuBacktrackSolver(PidokuRules rules) {
        this.rules = rules;
    }

    /**
     * Solves trying digits in a fixed order, so the same grid always gets the same solution.
     *
     * @return the solved puzzle, or null if the grid breaks the rules or has no solution
     */
    @Override
    public PidokuPuzzle solve(int[][] puzzle) {
        return search(puzzle, null);
    }

    /**
     * Solves trying digits in an order drawn from {@code random}. On an empty grid this makes a
     * random complete grid, and the same random sequence always makes the same grid.
     *
     * @return the solved puzzle, or null if the grid breaks the rules or has no solution
     */
    public PidokuPuzzle solve(int[][] puzzle, RandomGenerator random) {
        return search(puzzle, Objects.requireNonNull(random, "random"));
    }

    /** Solutions of {@code puzzle}, counting up to {@link #UNIQUENESS_CUTOFF}. */
    public int countSolutions(int[][] puzzle) {
        return countSolutions(puzzle, UNIQUENESS_CUTOFF);
    }

    /** Solutions of {@code puzzle}, counting up to {@code maxSolutions}. */
    public int countSolutions(int[][] puzzle, int maxSolutions) {
        SolverSearchState state = SolverSearchState.from(rules, puzzle);
        if (!state.isValid()) {
            return 0;
        }
        int[] digits = rules.getUniqueDigits();
        return countBacktrack(state, digits, maxSolutions);
    }

    /** @param random the digit order's source, or null to try digits in a fixed order */
    private PidokuPuzzle search(int[][] puzzle, RandomGenerator random) {
        SolverSearchState state = SolverSearchState.from(rules, puzzle);
        if (!state.isValid()) {
            return null;
        }
        if (solveBacktrack(state, rules.getUniqueDigits(), random)) {
            return new PidokuPuzzle(puzzle, state.toArray());
        }
        return null;
    }

    private int countBacktrack(SolverSearchState state, int[] digits, int maxSolutions) {
        BoardPosition next = SearchUtils.findNextCell(state);
        if (next == null) {
            return 1;
        }
        int solutions = 0;
        for (int value : digits) {
            if (!state.canPlace(next, value)) {
                continue;
            }
            state.place(next, value);
            solutions += countBacktrack(state, digits, maxSolutions - solutions);
            state.remove(next);
            if (solutions >= maxSolutions) {
                return solutions;
            }
        }
        return solutions;
    }

    private boolean solveBacktrack(SolverSearchState state, int[] digits, RandomGenerator random) {
        BoardPosition next = SearchUtils.findNextCell(state);
        if (next == null) {
            return true;
        }
        int[] order = digits.clone();
        if (random != null) {
            shuffleValues(order, random);
        }
        for (int value : order) {
            if (!state.canPlace(next, value)) {
                continue;
            }
            state.place(next, value);
            if (solveBacktrack(state, order, random)) {
                return true;
            }
            state.remove(next);
        }
        return false;
    }

    private static void shuffleValues(int[] digits, RandomGenerator random) {
        for (int i = digits.length - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            int temp = digits[i];
            digits[i] = digits[swapIndex];
            digits[swapIndex] = temp;
        }
    }
}
