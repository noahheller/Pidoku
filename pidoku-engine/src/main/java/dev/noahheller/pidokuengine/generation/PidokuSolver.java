package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.PidokuPuzzle;

public interface PidokuSolver {
    PidokuPuzzle solve(int[][] puzzle);
}
