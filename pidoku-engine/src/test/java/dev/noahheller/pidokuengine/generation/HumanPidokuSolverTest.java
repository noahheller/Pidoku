package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.searching.SolveTrace;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import org.junit.jupiter.api.Test;

import static dev.noahheller.pidokuengine.PidokuTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanPidokuSolverTest {

    private final HumanPidokuSolver solver = new HumanPidokuSolver(rules());

    @Test
    void singleMissingCellIsAHiddenSingleInItsBox() {
        int[][] solution = fullSolution(11);
        int[][] puzzle = punchHoles(solution, 1, 11);

        SolveTrace trace = solver.solve(puzzle).getSolveTrace();

        assertTrue(trace.solved);
        assertFalse(trace.requiresGuessing);
        assertEquals(1, trace.getPlacementCount());
        // A lone gap is a hidden single in a box, a row, a column and a naked single;
        // the solver should take the easiest reading of it.
        assertEquals(StrategyEnum.HIDDEN_SINGLE_BOX, trace.getHardestStrategy());
        assertEquals(1, trace.getSteps().get(0).getOptions());
        // Only one option, but with one empty cell it isn't a bottleneck.
        assertEquals(0, trace.getBottleneckSteps());
    }

    @Test
    void reconstructsTheOriginalSolution() {
        int[][] solution = fullSolution(12);
        int[][] puzzle = punchHoles(solution, 20, 12);

        PidokuPuzzle result = solver.solve(puzzle);

        assertTrue(result.getSolveTrace().solved);
        assertEquals(20, result.getSolveTrace().getPlacementCount());
        assertArrayEquals(solution, result.getSolution());
    }

    @Test
    void emptyBoardRequiresGuessing() {
        SolveTrace trace = solver.solve(new int[SIZE][SIZE]).getSolveTrace();
        assertFalse(trace.solved);
        assertTrue(trace.requiresGuessing);
    }

    /** Regression: invalid givens used to be half-loaded and then "solved". */
    @Test
    void rejectsGivensThatBreakTheRules() {
        int[][] puzzle = fullSolution(14);
        // 3 appears once per row; overwrite row 0's 4 with a second 3.
        for (int c = 0; c < SIZE; c++) {
            if (puzzle[0][c] == 4) puzzle[0][c] = 3;
        }
        // Leave some cells for the solver to "fill" (away from row 0).
        puzzle[8][8] = 0;
        puzzle[7][7] = 0;
        puzzle[6][6] = 0;

        assertInvalid(puzzle);
    }

    @Test
    void rejectsDigitsThatAreNotPartOfThisPidoku() {
        int[][] puzzle = punchHoles(fullSolution(15), 3, 15);
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (puzzle[r][c] != 0) {
                    puzzle[r][c] = 7;
                    assertInvalid(puzzle);
                    return;
                }
            }
        }
    }

    private void assertInvalid(int[][] puzzle) {
        PidokuPuzzle result = solver.solve(puzzle);
        SolveTrace trace = result.getSolveTrace();
        assertTrue(trace.invalid, trace.toString());
        assertFalse(trace.solved);
        assertNull(result.getSolution(), "an unsolved trace must not pretend to have a solution");
    }

    @Test
    void unsolvedPuzzlesHaveNoSolution() {
        assertNull(solver.solve(new int[SIZE][SIZE]).getSolution());
    }

    @Test
    void doesNotMutateInput() {
        int[][] puzzle = punchHoles(fullSolution(13), 30, 13);
        int[][] before = puzzle.clone();
        for (int i = 0; i < before.length; i++) before[i] = puzzle[i].clone();

        solver.solve(puzzle);

        assertArrayEquals(before, puzzle);
    }
}
