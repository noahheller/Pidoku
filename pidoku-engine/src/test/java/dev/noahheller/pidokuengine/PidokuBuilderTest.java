package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.generation.HumanPidokuSolver;
import dev.noahheller.pidokuengine.generation.PidokuBacktrackSolver;
import dev.noahheller.pidokuengine.searching.SolveTrace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import dev.noahheller.pidokuengine.strategy.Tier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static dev.noahheller.pidokuengine.PidokuTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: every premade difficulty produces a puzzle that is valid, unique,
 * human-solvable, and accepted by the profile that requested it.
 */
class PidokuBuilderTest {

    /** One builder for every test: it's thread-safe and keeps no state between calls. */
    private static final PidokuBuilder BUILDER = PidokuBuilder.standard(rules());

    static Stream<DifficultyProfile> profiles() {
        return Stream.of(DifficultyProfile.premadeProfiles());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void generatesValidUniquePuzzleForProfile(DifficultyProfile profile) {
        PidokuPuzzle puzzle = BUILDER.generate(profile, 1234);
        assertNotNull(puzzle, profile.name + " generation returned null");

        int[][] givens = puzzle.getPuzzle();
        int[][] solution = puzzle.getSolution();

        assertValidSolution(solution);
        assertConsistent(givens, solution);
        assertEquals(1, new PidokuBacktrackSolver(rules()).countSolutions(givens), "puzzle must be unique");

        // Re-solve independently rather than trusting the trace the generator returned.
        SolveTrace trace = new HumanPidokuSolver(rules()).solve(givens).getSolveTrace();
        assertTrue(trace.solved, "must be solvable without guessing");
        assertTrue(profile.accepts(trace, countClues(givens)),
                profile.name + " rejected its own puzzle: " + trace + ", clues=" + countClues(givens));
    }

    /** Regression: an unsatisfiable profile used to run ~2 minutes before giving up. */
    @Test
    void givesUpWhenTheTimeoutRunsOut() {
        DifficultyProfile impossible = new DifficultyProfile.Builder("Impossible")
                .clueRange(50, 65).floor(Tier.EXPERT).ceiling(Tier.EXPERT).build();
        long start = System.nanoTime();
        assertNull(BUILDER.generate(impossible, 1, Duration.ofMillis(500)));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 3_000, "took " + elapsedMs + " ms");
    }

    @Test
    void sameSeedProducesSamePuzzleWhateverRanBefore() {
        DifficultyProfile easy = DifficultyProfile.easy();
        int[][] first = BUILDER.generate(easy, 99).getPuzzle();
        BUILDER.generate(easy, 5);
        assertArrayEquals(first, BUILDER.generate(easy, 99).getPuzzle());
    }

    @Test
    void differentSeedsProduceDifferentPuzzles() {
        DifficultyProfile trivial = DifficultyProfile.trivial();
        assertNotEquals(Arrays.deepToString(BUILDER.generate(trivial, 1).getPuzzle()),
                Arrays.deepToString(BUILDER.generate(trivial, 2).getPuzzle()));
    }

    /** Many threads sharing one builder get exactly the puzzles they would get one at a time. */
    @Test
    void concurrentCallsMatchSequentialCalls() throws Exception {
        DifficultyProfile trivial = DifficultyProfile.trivial();
        long[] seeds = LongStream.rangeClosed(1, 24).toArray();

        List<String> sequential = new ArrayList<>();
        for (long seed : seeds) {
            sequential.add(Arrays.deepToString(BUILDER.generate(trivial, seed).getPuzzle()));
        }

        List<String> concurrent = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<PidokuPuzzle>> futures = new ArrayList<>();
            for (long seed : seeds) {
                futures.add(pool.submit(() -> BUILDER.generate(trivial, seed)));
            }
            for (Future<PidokuPuzzle> future : futures) {
                concurrent.add(Arrays.deepToString(future.get().getPuzzle()));
            }
        }

        assertEquals(sequential, concurrent);
    }
}
