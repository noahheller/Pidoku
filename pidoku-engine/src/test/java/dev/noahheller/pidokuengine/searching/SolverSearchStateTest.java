package dev.noahheller.pidokuengine.searching;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static dev.noahheller.pidokuengine.PidokuTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/** The shared candidate cache always agrees with working the candidates out from scratch. */
class SolverSearchStateTest {

    private static final int BOARDS = 300;
    private static final int OPERATIONS = 60;

    @Test
    void candidateMasksMatchAFreshComputationAfterEveryChange() {
        PidokuRules rules = rules();
        int[] digits = rules.getUniqueDigits();
        for (long seed = 0; seed < BOARDS; seed++) {
            int[][] solution = fullSolution(seed);
            SolverSearchState state = SolverSearchState.from(rules, punchHoles(solution, 40 + (int) (seed % 25), seed));
            SplittableRandom random = new SplittableRandom(seed);
            List<BoardPosition> placed = new ArrayList<>();

            for (int op = 0; op < OPERATIONS; op++) {
                int row = random.nextInt(SIZE);
                int col = random.nextInt(SIZE);
                switch (random.nextInt(4)) {
                    case 0 -> {
                        // Place a digit the cell allows (not necessarily the solution's).
                        if (!state.isEmpty(row, col)) break;
                        for (int digit : digits) {
                            if (state.canPlace(row, col, digit)) {
                                state.place(row, col, digit);
                                placed.add(new BoardPosition(row, col));
                                break;
                            }
                        }
                    }
                    case 1 -> {
                        if (placed.isEmpty()) break;
                        state.remove(placed.remove(random.nextInt(placed.size())));
                    }
                    // Often while the cache is fresh, since checking reads it after every operation.
                    default -> state.eliminate(row, col, digits[random.nextInt(digits.length)]);
                }
                assertMatches(state, digits, "seed " + seed + " operation " + op);
            }
        }
    }

    @Test
    void filledCellsHaveNoCandidates() {
        PidokuRules rules = rules();
        int[][] solution = fullSolution(7);
        SolverSearchState state = SolverSearchState.from(rules, punchHoles(solution, 50, 7));
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (!state.isEmpty(row, col)) {
                    assertEquals(0, state.candidateMask(row, col));
                    assertEquals(List.of(), SearchUtils.getCandidates(state, row, col));
                }
            }
        }
    }

    @Test
    void getCandidatesKeepsTheRulesDigitOrder() {
        SolverSearchState state = SolverSearchState.from(rules(), new int[SIZE][SIZE]);
        assertEquals(List.of(3, 1, 4, 5, 9, 2, 6), SearchUtils.getCandidates(state, 0, 0));
    }

    private static void assertMatches(SolverSearchState state, int[] digits, String where) {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int expected = 0;
                if (state.isEmpty(row, col)) {
                    for (int digit : digits) {
                        if (!state.isEliminated(row, col, digit) && state.canPlace(row, col, digit)) expected |= 1 << digit;
                    }
                }
                int actual = state.candidateMask(row, col);
                if (actual != expected) {
                    fail(where + ": cell (" + row + "," + col + ") has mask " + Integer.toBinaryString(actual)
                            + ", expected " + Integer.toBinaryString(expected));
                }
            }
        }
    }
}
