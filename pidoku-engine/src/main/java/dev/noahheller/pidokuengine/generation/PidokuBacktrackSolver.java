package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.Random;

public class PidokuBacktrackSolver implements PidokuSolver{
    private final PidokuRules rules;
    private Random random;
    private final int maxSolutionCountCutoff;

    public PidokuBacktrackSolver(PidokuRules rules, long seed) {
        this(rules, seed, 2);
    }

    public PidokuBacktrackSolver(PidokuRules rules, long seed, int maxSolutionCountCutoff) {
        this.rules = rules;
        this.random = new Random(seed);
        this.maxSolutionCountCutoff = maxSolutionCountCutoff;
    }

    public PidokuPuzzle solve(int[][] puzzle) {
        this.random = new Random(random.nextLong());
        SolverSearchState state = SolverSearchState.from(rules, puzzle);
        if (!state.isValid()) {
            return null;
        }
        int[] digits = rules.getUniqueDigits();
        if (solveBacktrack(state, digits)) {
            return new PidokuPuzzle(puzzle, state.toArray());
        }
        return null;
    }

    public int countSolutions(int[][] puzzle) {
        return countSolutions(puzzle, maxSolutionCountCutoff);
    }

    public int countSolutions(int[][] puzzle, int maxSolutions) {
        SolverSearchState state = SolverSearchState.from(rules, puzzle);
        if (!state.isValid()) {
            return 0;
        }
        int[] digits = rules.getUniqueDigits();
        return countBacktrack(state, digits, maxSolutions);
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


    public boolean solveBacktrack(SolverSearchState state, int[] digits) {
        BoardPosition next = SearchUtils.findNextCell(state);
        if (next == null) {
            return true;
        }
        int[] order = digits.clone();
        shuffleValues(order);

        for (int value : order) {
            if (!state.canPlace(next, value)) {
                continue;
            }
            state.place(next, value);
            if (solveBacktrack(state, order)) {
                return true;
            }
            state.remove(next);
        }
        return false;

    }

    public void shuffleValues(int[] digits) {
        for (int i = digits.length - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            int temp = digits[i];
            digits[i] = digits[swapIndex];
            digits[swapIndex] = temp;
        }

    }
}
