package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.generation.PidokuGenerator;
import dev.noahheller.pidokuengine.generation.PidokuSolver;

public class PidokuBuilder {

    private final PidokuRules rules;
    private final PidokuSolver quickSolve;
    private final int solverAttempts;
    private final PidokuGenerator generator;
    private final int genAttempts;

    public PidokuBuilder(PidokuRules rules, PidokuSolver quickSolve, int solverAttempts, PidokuGenerator generator, int genAttempts) {
        this.rules = rules;
        this.quickSolve = quickSolve;
        this.solverAttempts = solverAttempts;
        this.generator = generator;
        this.genAttempts = genAttempts;
    }

    public PidokuBuilder(PidokuRules rules, PidokuSolver quickSolve, PidokuGenerator generator) {
        this(rules, quickSolve, 50, generator, 20);
    }

    public PidokuPuzzle generate(DifficultyProfile profile) {
        int size = rules.getSize();
        for (int attempts = 0; attempts < solverAttempts; attempts++) {
            PidokuPuzzle solution = quickSolve.solve(new int[size][size]);
            if (solution == null) continue;
            for (int i = 0; i < genAttempts; i++) {
                PidokuPuzzle puzzle = generator.buildPuzzle(solution.getSolution(), profile);
                if (puzzle != null && puzzle.getPuzzle() != null) {
                    return puzzle;
                }
            }
        }
        return null;
    }

}
