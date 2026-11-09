package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
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
                // Every move findAll reports must be sound, not only the one that gets applied,
                // and finding moves must leave the board untouched.
                for (StrategyEnum strategy : StrategyEnum.values()) {
                    Snapshot before = Snapshot.of(state);
                    for (Move move : strategy.getStrategy().findAll(state)) {
                        String violation = findViolation(move, solution);
                        if (violation != null) violations.add(strategy + " findAll on seed " + seed + ": " + violation);
                    }
                    if (!before.equals(Snapshot.of(state))) {
                        violations.add(strategy + " findAll modified the board on seed " + seed);
                    }
                }
                if (!violations.isEmpty()) break;

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
        assertTrue(fired.getOrDefault(StrategyEnum.HIDDEN_SINGLE_BOX, 0) > 0);
        assertTrue(violations.isEmpty(),
                violations.size() + " unsound deductions:\n  " + String.join("\n  ", violations));
    }

    private static String findViolation(Move move, int[][] solution) {
        if (move.isPlacement()) {
            BoardPosition p = move.getPosition();
            int truth = solution[p.getRow()][p.getCol()];
            return move.getValue() == truth ? null : move + ", solution has " + truth;
        }
        for (Move.Candidate c : move.getEliminations()) {
            if (solution[c.position().getRow()][c.position().getCol()] == c.value()) {
                return "would eliminate true digit " + c.value() + " from " + c.position();
            }
        }
        return null;
    }

    /** Grid values plus every soft elimination, for checking that findAll is read-only. */
    private record Snapshot(String grid, String eliminated) {
        static Snapshot of(SolverSearchState state) {
            StringBuilder eliminated = new StringBuilder();
            for (int r = 0; r < SIZE; r++)
                for (int c = 0; c < SIZE; c++)
                    for (int v = 0; v <= 9; v++)
                        eliminated.append(state.isEliminated(r, c, v) ? '1' : '0');
            return new Snapshot(java.util.Arrays.deepToString(state.toArray()), eliminated.toString());
        }
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
