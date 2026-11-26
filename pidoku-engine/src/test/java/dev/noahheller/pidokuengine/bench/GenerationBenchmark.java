package dev.noahheller.pidokuengine.bench;

import dev.noahheller.pidokuengine.DifficultyProfile;
import dev.noahheller.pidokuengine.PidokuBuilder;
import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.generation.HumanPidokuGenerator;
import dev.noahheller.pidokuengine.generation.HumanPidokuSolver;
import dev.noahheller.pidokuengine.generation.PidokuBacktrackSolver;
import dev.noahheller.pidokuengine.utils.ArrayUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rough timing of puzzle generation, for before/after comparisons of engine changes. Not a test
 * and not run in CI: {@code ./gradlew :pidoku-engine:benchmark} (optionally {@code --args=<seeds>}).
 *
 * <p>Prints (a) generation time per premade profile over seeds 1..n, with the share of it spent in
 * the backtracker's uniqueness check (the rest is almost all the human solver). Then (b) the human
 * solver and (c) the uniqueness check on their own, on generated puzzles with one more clue
 * removed. Generation runs the check on every removal and swap it tries but the human solver
 * only on those that pass, so (b) and (c) per call don't give the split; (a) does.
 */
public final class GenerationBenchmark {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);
    private static final int SOLVER_ROUNDS = 5;

    public static void main(String[] args) {
        int seeds = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        PidokuRules rules = new PidokuRules(9);
        TimedBacktrackSolver timed = new TimedBacktrackSolver(rules);
        PidokuBuilder builder = new PidokuBuilder(rules, timed, new HumanPidokuGenerator(rules, timed));

        System.out.println("Warming up...");
        for (DifficultyProfile profile : DifficultyProfile.premadeProfiles()) {
            builder.generate(profile, 1000, TIMEOUT);
        }
        timed.nanos = 0;

        System.out.printf("%n(a) generate(), seeds 1..%d%n", seeds);
        System.out.printf("%-10s %10s %10s %10s %13s%n", "profile", "median ms", "mean ms", "max ms", "uniqueness %");
        List<int[][]> generated = new ArrayList<>();
        long totalNanos = 0;
        for (DifficultyProfile profile : DifficultyProfile.premadeProfiles()) {
            long[] millis = new long[seeds];
            long checkBefore = timed.nanos;
            long profileNanos = 0;
            for (int i = 0; i < seeds; i++) {
                long start = System.nanoTime();
                PidokuPuzzle puzzle = builder.generate(profile, i + 1, TIMEOUT);
                long elapsed = System.nanoTime() - start;
                totalNanos += elapsed;
                profileNanos += elapsed;
                millis[i] = elapsed / 1_000_000;
                if (puzzle != null) generated.add(puzzle.getPuzzle());
            }
            long[] sorted = millis.clone();
            Arrays.sort(sorted);
            System.out.printf("%-10s %10d %10d %10d %12.0f%%%n", profile.name, sorted[seeds / 2],
                    Arrays.stream(millis).sum() / seeds, sorted[seeds - 1],
                    100.0 * (timed.nanos - checkBefore) / profileNanos);
        }
        System.out.printf("%-10s %10s %10d %10s %12.0f%%%n", "all", "", totalNanos / 1_000_000 / seeds, "",
                100.0 * timed.nanos / totalNanos);

        List<int[][]> boards = new ArrayList<>();
        for (int[][] puzzle : generated) {
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    if (puzzle[r][c] == PidokuRules.EMPTY) continue;
                    int[][] board = ArrayUtils.copy(puzzle);
                    board[r][c] = PidokuRules.EMPTY;
                    boards.add(board);
                }
            }
        }

        HumanPidokuSolver human = new HumanPidokuSolver(rules);
        // The plain solver, so the timing wrapper's clock calls don't count in (c).
        PidokuBacktrackSolver backtrack = new PidokuBacktrackSolver(rules);
        long sink = 0;

        long start = System.nanoTime();
        for (int round = 0; round < SOLVER_ROUNDS; round++) {
            for (int[][] board : boards) sink += human.solve(ArrayUtils.copy(board)).getSolveTrace().getTotalSteps();
        }
        double humanUs = (System.nanoTime() - start) / 1000.0 / (SOLVER_ROUNDS * (double) boards.size());

        start = System.nanoTime();
        for (int round = 0; round < SOLVER_ROUNDS; round++) {
            for (int[][] board : boards) sink += backtrack.countSolutions(board);
        }
        double backtrackUs = (System.nanoTime() - start) / 1000.0 / (SOLVER_ROUNDS * (double) boards.size());

        System.out.printf("%nOn %d boards (generated puzzles with one more clue removed), %d rounds:%n",
                boards.size(), SOLVER_ROUNDS);
        System.out.printf("(b) HumanPidokuSolver.solve           %8.1f us/call%n", humanUs);
        System.out.printf("(c) PidokuBacktrackSolver.countSolutions %5.1f us/call%n", backtrackUs);
        System.out.println("(checksum " + sink + ")");
    }

    /** Adds up the time generation spends checking uniqueness. Single-threaded use only. */
    private static final class TimedBacktrackSolver extends PidokuBacktrackSolver {
        long nanos;

        TimedBacktrackSolver(PidokuRules rules) {
            super(rules);
        }

        @Override
        public int countSolutions(int[][] puzzle) {
            long start = System.nanoTime();
            try {
                return super.countSolutions(puzzle);
            } finally {
                nanos += System.nanoTime() - start;
            }
        }
    }
}
