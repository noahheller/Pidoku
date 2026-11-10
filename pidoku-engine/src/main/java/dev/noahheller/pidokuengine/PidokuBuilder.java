package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.generation.HumanPidokuGenerator;
import dev.noahheller.pidokuengine.generation.PidokuBacktrackSolver;
import dev.noahheller.pidokuengine.generation.PidokuGenerator;

import java.time.Duration;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Generates puzzles for a {@link DifficultyProfile}.
 *
 * <p>Thread-safe: every field is final and never changes, and all randomness comes from the
 * seed passed to {@link #generate}. Each call makes its own random number generator from that
 * seed, so one builder can be shared by any number of threads, and the same seed and profile
 * always give the same puzzle, whatever ran before (as long as generation finishes before the
 * timeout).
 */
public class PidokuBuilder {

    /**
     * Generous enough for every premade profile (Hard and Fiendish usually take a few
     * seconds, rarely over 15), short enough that an unsatisfiable profile fails fast.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final PidokuRules rules;
    private final PidokuBacktrackSolver solver;
    private final int solverAttempts;
    private final PidokuGenerator generator;
    private final int genAttempts;

    public PidokuBuilder(PidokuRules rules, PidokuBacktrackSolver solver, int solverAttempts,
                         PidokuGenerator generator, int genAttempts) {
        this.rules = rules;
        this.solver = solver;
        this.solverAttempts = solverAttempts;
        this.generator = generator;
        this.genAttempts = genAttempts;
    }

    public PidokuBuilder(PidokuRules rules, PidokuBacktrackSolver solver, PidokuGenerator generator) {
        this(rules, solver, 50, generator, 20);
    }

    /** The standard builder: the backtracking solver and the human-strategy generator. */
    public static PidokuBuilder standard(PidokuRules rules) {
        PidokuBacktrackSolver solver = new PidokuBacktrackSolver(rules);
        return new PidokuBuilder(rules, solver, new HumanPidokuGenerator(rules, solver));
    }

    /** {@link #generate(DifficultyProfile, long, Duration)} with {@link #DEFAULT_TIMEOUT}. */
    public PidokuPuzzle generate(DifficultyProfile profile, long seed) {
        return generate(profile, seed, DEFAULT_TIMEOUT);
    }

    /**
     * Generates a puzzle the profile accepts, or returns null if none was found within
     * the attempt limits or before {@code timeout} ran out. The deadline is checked
     * between attempts, so a call can overrun it by up to one attempt (well under a
     * second for 9x9).
     *
     * @param seed decides every random choice; store it to identify the puzzle
     */
    public PidokuPuzzle generate(DifficultyProfile profile, long seed, Duration timeout) {
        // Local to this call: never shared with another call or thread.
        RandomGenerator random = new SplittableRandom(seed);
        long deadline = System.nanoTime() + timeout.toNanos();
        int size = rules.getSize();
        for (int attempts = 0; attempts < solverAttempts; attempts++) {
            if (System.nanoTime() - deadline >= 0) return null;
            PidokuPuzzle solution = solver.solve(new int[size][size], random);
            if (solution == null) continue;
            for (int i = 0; i < genAttempts; i++) {
                if (System.nanoTime() - deadline >= 0) return null;
                PidokuPuzzle puzzle = generator.buildPuzzle(solution.getSolution(), profile, random);
                if (puzzle != null && puzzle.getPuzzle() != null) {
                    return puzzle;
                }
            }
        }
        return null;
    }

}
