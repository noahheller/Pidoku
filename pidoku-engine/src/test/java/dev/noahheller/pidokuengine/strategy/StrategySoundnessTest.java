package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static dev.noahheller.pidokuengine.PidokuTestSupport.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property test: a logical strategy must never place a wrong digit or eliminate
 * the true digit from a cell. We start from a known solution, blank cells, and
 * run the human strategies, checking every step against that solution.
 *
 * Puzzles here are not guaranteed unique, but sound deductions hold for every
 * completion, so the original solution must always survive.
 */
class StrategySoundnessTest {

    private static final int PUZZLES = 2000;

    @Test
    void strategiesNeverContradictTheSolution() {
        PidokuRules rules = rules();
        Map<StrategyEnum, Integer> fired = new EnumMap<>(StrategyEnum.class);
        List<String> violations = new ArrayList<>();

        for (long seed = 0; seed < PUZZLES; seed++) {
            int[][] solution = fullSolution(seed);
            int holes = 45 + (int) (seed % 20); // 45..64 blanks: easy through very sparse
            SolverSearchState state = SolverSearchState.from(rules, punchHoles(solution, holes, seed));

            boolean progressed = true;
            while (progressed && !SearchUtils.isSolved(state)) {
                progressed = false;
                for (StrategyEnum strategy : StrategyEnum.values()) {
                    StrategyResult result = strategy.getStrategy().apply(state);
                    if (!result.madeProgress()) continue;

                    fired.merge(strategy, 1, Integer::sum);
                    String violation = findViolation(state, solution);
                    if (violation != null) {
                        // State is now corrupt; record and move on to the next puzzle.
                        violations.add(strategy + " on seed " + seed + ": " + violation);
                        progressed = false;
                    } else {
                        progressed = true;
                    }
                    break;
                }
            }
        }

        System.out.println("Strategy firings across " + PUZZLES + " puzzles: " + fired);
        assertTrue(fired.getOrDefault(StrategyEnum.NAKED_SINGLE, 0) > 0);
        assertTrue(fired.getOrDefault(StrategyEnum.HIDDEN_SINGLE, 0) > 0);
        assertTrue(violations.isEmpty(),
                violations.size() + " unsound deductions:\n  " + String.join("\n  ", violations));
    }

    /** Returns a description of the first contradiction with {@code solution}, or null if none. */
    private static String findViolation(SolverSearchState state, int[][] solution) {
        int[][] grid = state.toArray();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (grid[r][c] != PidokuRules.EMPTY) {
                    if (grid[r][c] != solution[r][c]) {
                        return "placed " + grid[r][c] + " at (" + r + "," + c + "), solution has " + solution[r][c];
                    }
                } else if (state.isEliminated(r, c, solution[r][c])) {
                    return "eliminated true digit " + solution[r][c] + " from (" + r + "," + c + ")";
                }
            }
        }
        return null;
    }
}
