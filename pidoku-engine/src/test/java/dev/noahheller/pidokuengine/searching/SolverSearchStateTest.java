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

    /**
     * canPlace reads per-unit masks kept up to date by place and remove. It must agree with the
     * rule worked out from the counts, for every cell and value, filled or not, legal or not.
     */
    @Test
    void canPlaceMatchesTheRuleWorkedOutFromTheCounts() {
        PidokuRules rules = rules();
        int[] digits = rules.getUniqueDigits();
        for (long seed = 0; seed < BOARDS; seed++) {
            int[][] solution = fullSolution(seed);
            // Includes nearly full boards, so the "last cell in a unit" rule comes up often.
            SolverSearchState state = SolverSearchState.from(rules, punchHoles(solution, 2 + (int) (seed % 60), seed));
            SplittableRandom random = new SplittableRandom(seed);
            List<BoardPosition> placed = new ArrayList<>();
            assertCanPlaceMatches(state, rules, "seed " + seed + " start");

            for (int op = 0; op < OPERATIONS; op++) {
                int row = random.nextInt(SIZE);
                int col = random.nextInt(SIZE);
                if (random.nextInt(3) > 0 && state.isEmpty(row, col)) {
                    // Usually a legal digit; sometimes the solution's, which may not be legal any more.
                    int digit = random.nextInt(4) == 0 ? solution[row][col] : digits[random.nextInt(digits.length)];
                    if (state.canPlace(row, col, digit)) {
                        state.place(row, col, digit);
                        placed.add(new BoardPosition(row, col));
                    }
                } else if (!placed.isEmpty()) {
                    state.remove(placed.remove(random.nextInt(placed.size())));
                } else if (!state.isEmpty(row, col)) {
                    // Remove a given too: the masks must also widen again.
                    state.remove(new BoardPosition(row, col));
                }
                assertCanPlaceMatches(state, rules, "seed " + seed + " operation " + op);
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

    private static void assertCanPlaceMatches(SolverSearchState state, PidokuRules rules, String where) {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                for (int value = 0; value <= 9; value++) {
                    boolean expected = referenceCanPlace(state, rules, row, col, value);
                    if (state.canPlace(row, col, value) != expected) {
                        fail(where + ": canPlace(" + row + ", " + col + ", " + value + ") should be " + expected);
                    }
                }
            }
        }
    }

    /** canPlace as it was before the unit masks: straight from the counts. */
    private static boolean referenceCanPlace(SolverSearchState state, PidokuRules rules, int row, int col, int value) {
        int box = rules.boxIndex(row, col);
        int freq = rules.getDigitFrequency(value);
        if (state.getRowDigitCount(row, value) + 1 > freq) return false;
        if (state.getColDigitCount(col, value) + 1 > freq) return false;
        if (state.getBoxDigitCount(box, value) + 1 > freq) return false;
        int rowFilled = 0, colFilled = 0, boxFilled = 0;
        for (int i = 0; i < SIZE; i++) {
            if (!state.isEmpty(row, i)) rowFilled++;
            if (!state.isEmpty(i, col)) colFilled++;
            int r = (box / 3) * 3 + i / 3, c = (box % 3) * 3 + i % 3;
            if (!state.isEmpty(r, c)) boxFilled++;
        }
        if (rowFilled + 1 == SIZE && !wouldComplete(rules, d -> state.getRowDigitCount(row, d), value)) return false;
        if (colFilled + 1 == SIZE && !wouldComplete(rules, d -> state.getColDigitCount(col, d), value)) return false;
        return boxFilled + 1 != SIZE || wouldComplete(rules, d -> state.getBoxDigitCount(box, d), value);
    }

    private static boolean wouldComplete(PidokuRules rules, java.util.function.IntUnaryOperator counts, int value) {
        for (int digit = rules.MIN_VALUE; digit <= rules.MAX_VALUE; digit++) {
            if (counts.applyAsInt(digit) + (digit == value ? 1 : 0) != rules.getDigitFrequency(digit)) return false;
        }
        return true;
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
