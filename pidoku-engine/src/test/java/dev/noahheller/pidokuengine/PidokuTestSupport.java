package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.generation.PidokuBacktrackSolver;
import dev.noahheller.pidokuengine.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public final class PidokuTestSupport {

    public static final int SIZE = 9;
    /** First 9 digits of pi, sorted: every row, column and box must contain exactly this multiset. */
    public static final int[] UNIT_MULTISET = {1, 1, 2, 3, 4, 5, 5, 6, 9};

    private PidokuTestSupport() {
    }

    public static PidokuRules rules() {
        return new PidokuRules(SIZE);
    }

    public static int[][] fullSolution(long seed) {
        PidokuPuzzle solved = new PidokuBacktrackSolver(rules()).solve(new int[SIZE][SIZE], new SplittableRandom(seed));
        assertNotNull(solved, "backtracker should always fill an empty board");
        return solved.getSolution();
    }

    /** Blanks {@code holes} random cells of {@code solution}. The result may have multiple solutions. */
    public static int[][] punchHoles(int[][] solution, int holes, long seed) {
        int[][] puzzle = ArrayUtils.copy(solution);
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < SIZE * SIZE; i++) cells.add(i);
        Collections.shuffle(cells, new Random(seed));
        for (int i = 0; i < holes; i++) {
            int cell = cells.get(i);
            puzzle[cell / SIZE][cell % SIZE] = PidokuRules.EMPTY;
        }
        return puzzle;
    }

    public static void assertValidSolution(int[][] grid) {
        assertEquals(SIZE, grid.length);
        int box = (int) Math.sqrt(SIZE);
        for (int i = 0; i < SIZE; i++) {
            int[] row = new int[SIZE];
            int[] col = new int[SIZE];
            int[] boxCells = new int[SIZE];
            for (int j = 0; j < SIZE; j++) {
                row[j] = grid[i][j];
                col[j] = grid[j][i];
                boxCells[j] = grid[(i / box) * box + j / box][(i % box) * box + j % box];
            }
            assertUnit(row, "row " + i);
            assertUnit(col, "col " + i);
            assertUnit(boxCells, "box " + i);
        }
    }

    /** Every given in {@code puzzle} must agree with {@code solution}. */
    public static void assertConsistent(int[][] puzzle, int[][] solution) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (puzzle[r][c] != PidokuRules.EMPTY) {
                    assertEquals(solution[r][c], puzzle[r][c], "given at (" + r + "," + c + ") contradicts solution");
                }
            }
        }
    }

    public static int countClues(int[][] puzzle) {
        int clues = 0;
        for (int[] row : puzzle) {
            for (int v : row) {
                if (v != PidokuRules.EMPTY) clues++;
            }
        }
        return clues;
    }

    private static void assertUnit(int[] values, String label) {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        assertArrayEquals(UNIT_MULTISET, sorted, label + " should contain the digits of pi " + Arrays.toString(values));
    }
}
