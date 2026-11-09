package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.generation.PidokuGenerator;
import dev.noahheller.pidokuengine.generation.PidokuSolver;

import java.time.Duration;

public class PidokuBuilder {

    /**
     * Generous enough for every premade profile (Hard and Fiendish usually take a few
     * seconds, rarely over 15), short enough that an unsatisfiable profile fails fast.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

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

    /** {@link #generate(DifficultyProfile, Duration)} with {@link #DEFAULT_TIMEOUT}. */
    public PidokuPuzzle generate(DifficultyProfile profile) {
        return generate(profile, DEFAULT_TIMEOUT);
    }

    /**
     * Generates a puzzle the profile accepts, or returns null if none was found within
     * the attempt limits or before {@code timeout} ran out. The deadline is checked
     * between attempts, so a call can overrun it by up to one attempt (well under a
     * second for 9x9).
     */
    public PidokuPuzzle generate(DifficultyProfile profile, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        int size = rules.getSize();
        for (int attempts = 0; attempts < solverAttempts; attempts++) {
            if (System.nanoTime() - deadline >= 0) return null;
            PidokuPuzzle solution = quickSolve.solve(new int[size][size]);
            if (solution == null) continue;
            for (int i = 0; i < genAttempts; i++) {
                if (System.nanoTime() - deadline >= 0) return null;
                PidokuPuzzle puzzle = generator.buildPuzzle(solution.getSolution(), profile);
                if (puzzle != null && puzzle.getPuzzle() != null) {
                    return puzzle;
                }
            }
        }
        return null;
    }

}
