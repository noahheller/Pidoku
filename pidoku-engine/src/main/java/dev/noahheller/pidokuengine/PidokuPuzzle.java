package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.searching.SolveTrace;

public class PidokuPuzzle {
    private final int[][] puzzle;
    private final SolveTrace solveTrace;
    private final int[][] solution;

    public PidokuPuzzle(int[][] puzzle, SolveTrace solveTrace) {
        this.puzzle = puzzle;
        this.solveTrace = solveTrace;
        this.solution = solveTrace.reconstructSolution(puzzle);
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
    public int[][] getSolution() {
        return solution;
    }
}
