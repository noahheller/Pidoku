package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.searching.SolveTrace;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import org.junit.jupiter.api.Test;

import static dev.noahheller.pidokuengine.PidokuTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanPidokuSolverTest {

    private final HumanPidokuSolver solver = new HumanPidokuSolver(rules());

    @Test
    void singleMissingCellIsANakedSingle() {
        int[][] solution = fullSolution(11);
        int[][] puzzle = punchHoles(solution, 1, 11);

        SolveTrace trace = solver.solve(puzzle).getSolveTrace();

        assertTrue(trace.solved);
        assertFalse(trace.requiresGuessing);
        assertEquals(1, trace.getPlacementCount());
        assertEquals(StrategyEnum.NAKED_SINGLE, trace.getHardestStrategy());
        assertEquals(StrategyEnum.NAKED_SINGLE.getFirstTimeScore(), trace.getTotalScore());
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

    @Test
    void doesNotMutateInput() {
        int[][] puzzle = punchHoles(fullSolution(13), 30, 13);
        int[][] before = puzzle.clone();
        for (int i = 0; i < before.length; i++) before[i] = puzzle[i].clone();

        solver.solve(puzzle);

        assertArrayEquals(before, puzzle);
    }
}
