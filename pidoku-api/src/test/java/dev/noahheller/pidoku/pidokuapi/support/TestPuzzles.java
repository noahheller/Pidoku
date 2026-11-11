package dev.noahheller.pidoku.pidokuapi.support;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.GeneratedPuzzle;

import java.util.Map;

/**
 * A real engine puzzle (the bundled Medium puzzle from pidoku-frontend), for tests that need valid
 * data but not the slow generator.
 */
public final class TestPuzzles {

    public static final String GIVENS =
            "301019560000505900000300100400901506000001003000650409031050050002000000006003045";
    public static final String SOLUTION =
            "321419565614515932595326114415931526269541153153652419931254651542165391156193245";
    public static final int CLUE_COUNT = 31;

    private TestPuzzles() {
    }

    /** The fixture puzzle, labelled with any difficulty. */
    public static GeneratedPuzzle generated(Difficulty difficulty) {
        return generated(difficulty, 42);
    }

    public static GeneratedPuzzle generated(Difficulty difficulty, long seed) {
        return new GeneratedPuzzle(
                difficulty,
                9,
                GIVENS,
                SOLUTION,
                92,
                4.0,
                6.0,
                9,
                "INTERSECTION_REMOVAL",
                Map.of("HIDDEN_SINGLE_BOX", 42, "HIDDEN_SINGLE_LINE", 4, "NAKED_SINGLE", 4,
                        "NAKED_PAIR", 2, "INTERSECTION_REMOVAL", 1),
                "test",
                seed);
    }
}
