package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.PidokuRules;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static dev.noahheller.pidokuengine.PidokuTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PidokuBacktrackSolverTest {

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 42, 1234, 987654321})
    void fillsEmptyBoardWithValidSolution(long seed) {
        assertValidSolution(fullSolution(seed));
    }

    @Test
    void sameSeedProducesSameSolution() {
        // Determinism is what makes seed-based share codes possible.
        assertArrayEquals(fullSolution(77), fullSolution(77));
    }

    @Test
    void completedGridHasExactlyOneSolution() {
        PidokuBacktrackSolver solver = new PidokuBacktrackSolver(rules());
        assertEquals(1, solver.countSolutions(fullSolution(5)));
    }

    @Test
    void emptyGridHasMultipleSolutions() {
        PidokuBacktrackSolver solver = new PidokuBacktrackSolver(rules());
        assertEquals(2, solver.countSolutions(new int[SIZE][SIZE]), "count is capped at the cutoff");
    }

    @Test
    void solvesPartialPuzzleConsistentlyWithGivens() {
        int[][] solution = fullSolution(9);
        int[][] puzzle = punchHoles(solution, 40, 9);
        int[][] solved = new PidokuBacktrackSolver(rules()).solve(puzzle).getSolution();
        assertValidSolution(solved);
        assertConsistent(puzzle, solved);
    }

    @Test
    void rejectsBoardThatBreaksDigitFrequency() {
        int[][] puzzle = new int[SIZE][SIZE];
        puzzle[0][0] = 3;
        puzzle[0][1] = 3; // 3 appears once in pi's first nine digits
        PidokuBacktrackSolver solver = new PidokuBacktrackSolver(rules());
        assertNull(solver.solve(puzzle));
        assertEquals(0, solver.countSolutions(puzzle));
    }

    @Test
    void allowsRepeatedDigitUpToItsFrequency() {
        int[][] puzzle = new int[SIZE][SIZE];
        puzzle[0][0] = 1;
        puzzle[0][1] = 1; // 1 appears twice in pi's first nine digits
        PidokuBacktrackSolver solver = new PidokuBacktrackSolver(rules());
        assertTrue(solver.countSolutions(puzzle) > 0);
    }

    @Test
    void rejectsDigitNotInPi() {
        int[][] puzzle = new int[SIZE][SIZE];
        puzzle[4][4] = 7;
        assertNull(new PidokuBacktrackSolver(rules()).solve(puzzle));
    }

    @Test
    void emptyConstantIsZero() {
        assertEquals(0, PidokuRules.EMPTY);
    }
}
