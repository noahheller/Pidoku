package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.searching.SolveTrace;

public class PidokuPuzzle {
    private final int[][] puzzle;
    private final SolveTrace solveTrace;
    private final int[][] solution;

    public PidokuPuzzle(int[][] puzzle, SolveTrace solveTrace) {
        this.puzzle = puzzle;
        this.solveTrace = solveTrace;
        // Only a finished solve has a solution; a partial grid would look like one.
        this.solution = solveTrace.solved ? solveTrace.reconstructSolution(puzzle) : null;
    }
    public PidokuPuzzle(int[][] puzzle, int[][] solution) {
        this.puzzle = puzzle;
        this.solution = solution;
        this.solveTrace = null;
    }
    public int[][] getPuzzle() {
        return puzzle;
    }
    public SolveTrace getSolveTrace() {
        return solveTrace;
    }
    /** The full solution, or null if the solver couldn't finish (needs guessing, or invalid givens). */
    public int[][] getSolution() {
        return solution;
    }
}
