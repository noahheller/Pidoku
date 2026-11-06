package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.generation.HumanPidokuGenerator;
import dev.noahheller.pidokuengine.generation.HumanPidokuSolver;
import dev.noahheller.pidokuengine.generation.PidokuBacktrackSolver;
import dev.noahheller.pidokuengine.searching.SolveTrace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static dev.noahheller.pidokuengine.PidokuTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end: every premade difficulty produces a puzzle that is valid, unique,
 * human-solvable, and accepted by the profile that requested it.
 */
class PidokuBuilderTest {

    private static PidokuBuilder builder(long seed) {
        PidokuRules rules = rules();
        PidokuBacktrackSolver solver = new PidokuBacktrackSolver(rules, seed);
        HumanPidokuGenerator generator = new HumanPidokuGenerator(rules, solver, seed);
        return new PidokuBuilder(rules, solver, generator);
    }

    static Stream<DifficultyProfile> profiles() {
        return Stream.of(DifficultyProfile.premadeProfiles());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("profiles")
    void generatesValidUniquePuzzleForProfile(DifficultyProfile profile) {
        PidokuPuzzle puzzle = builder(1234).generate(profile);
        assertNotNull(puzzle, profile.name + " generation returned null");

        int[][] givens = puzzle.getPuzzle();
        int[][] solution = puzzle.getSolution();

        assertValidSolution(solution);
        assertConsistent(givens, solution);
        assertEquals(1, new PidokuBacktrackSolver(rules(), 0).countSolutions(givens), "puzzle must be unique");

        // Re-solve independently rather than trusting the trace the generator returned.
        SolveTrace trace = new HumanPidokuSolver(rules()).solve(givens).getSolveTrace();
        assertTrue(trace.solved, "must be solvable without guessing");
        assertTrue(profile.accepts(trace, countClues(givens)),
                profile.name + " rejected its own puzzle: " + trace + ", clues=" + countClues(givens));
    }

    @Test
    void sameSeedProducesSamePuzzle() {
        DifficultyProfile easy = DifficultyProfile.easy();
        assertArrayEquals(builder(99).generate(easy).getPuzzle(), builder(99).generate(easy).getPuzzle());
    }
}
