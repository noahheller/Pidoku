package dev.noahheller.pidokuengine.explain;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.generation.HumanPidokuSolver;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;
import dev.noahheller.pidokuengine.strategy.Move;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.Unit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static dev.noahheller.pidokuengine.PidokuTestSupport.SIZE;
import static dev.noahheller.pidokuengine.PidokuTestSupport.fullSolution;
import static dev.noahheller.pidokuengine.PidokuTestSupport.punchHoles;
import static dev.noahheller.pidokuengine.PidokuTestSupport.rules;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveExplainerTest {

    private static final PidokuRules RULES = rules();

    /** The tutorial's solution with some cells emptied. */
    private static SolverSearchState board(int[]... empty) {
        int[][] grid = new int[SIZE][SIZE];
        for (int i = 0; i < SIZE * SIZE; i++) grid[i / SIZE][i % SIZE] = TutorialExport.SOLUTION.charAt(i) - '0';
        for (int[] cell : empty) grid[cell[0]][cell[1]] = PidokuRules.EMPTY;
        return SolverSearchState.from(RULES, grid);
    }

    /** The move a strategy finds placing {@code digit} at a cell, reasoned about in {@code unit}. */
    private static Move placement(SolverSearchState state, StrategyEnum strategy, int row, int col, int digit, Unit unit) {
        return strategy.getStrategy().findAll(state).stream()
                .filter(m -> m.getPosition().equals(new BoardPosition(row, col)) && m.getValue() == digit)
                .filter(m -> m.getReason().units().getFirst().equals(unit))
                .findFirst().orElseThrow();
    }

    @Test
    void aDigitPiHasOnceIsTheOnlyCellThatCanTakeIt() {
        // The middle-left box is missing a 9 and two 1s; rows 4 and 6 already have their 9s.
        SolverSearchState state = board(new int[]{3, 1}, new int[]{4, 2}, new int[]{5, 0});
        Move move = placement(state, StrategyEnum.HIDDEN_SINGLE_BOX, 4, 2, 9, Unit.box(3));

        Explanation explanation = MoveExplainer.explain(StrategyEnum.HIDDEN_SINGLE_BOX, move, state);

        assertEquals("The middle-left box needs a 9, and this is the only cell in it that can take one.", explanation.text());
        assertEquals(new BoardPosition(4, 2), explanation.cell());
        assertEquals(9, explanation.digit());
        assertEquals(List.of(Unit.box(3)), explanation.units());
        assertEquals(List.of(new BoardPosition(4, 2)), explanation.pattern());
        // The 9s in rows 4 and 6 rule out the box's other two empty cells.
        assertEquals(List.of(new BoardPosition(3, 3), new BoardPosition(5, 8)), explanation.blockers());
    }

    @Test
    void aRowWithOneFiveNeedsItsSecond() {
        SolverSearchState state = board(new int[]{3, 1}, new int[]{3, 6}, new int[]{3, 8});
        Move move = placement(state, StrategyEnum.HIDDEN_SINGLE_LINE, 3, 6, 5, Unit.row(3));

        Explanation explanation = MoveExplainer.explain(StrategyEnum.HIDDEN_SINGLE_LINE, move, state);

        assertEquals("Row 4 already has one 5 and needs its second, and this is the only cell left in it that can take a 5.",
                explanation.text());
        // Column 2's two 5s and column 9's two 5s.
        assertEquals(List.of(new BoardPosition(5, 1), new BoardPosition(8, 1), new BoardPosition(0, 8), new BoardPosition(8, 8)),
                explanation.blockers());
    }

    @Test
    void twoSpotsForBothFivesAreBothFives() {
        // Box 6 (middle-right) is missing both its 5s and a 6; column 9 already has its two 5s.
        SolverSearchState state = board(new int[]{3, 6}, new int[]{3, 8}, new int[]{4, 7});
        Move move = placement(state, StrategyEnum.HIDDEN_SINGLE_BOX, 3, 6, 5, Unit.box(5));

        Explanation explanation = MoveExplainer.explain(StrategyEnum.HIDDEN_SINGLE_BOX, move, state);

        assertEquals("The middle-right box still needs both its 5s, and only these two cells can take a 5, so each of them is a 5.",
                explanation.text());
        assertEquals(List.of(new BoardPosition(3, 6), new BoardPosition(4, 7)), explanation.pattern());
    }

    @Test
    void aNakedSingleSaysRepeatedDigitsAreOnlyUsedUpInPairs() {
        SolverSearchState state = board(new int[]{0, 0});
        Move move = StrategyEnum.NAKED_SINGLE.getStrategy().findAll(state).getFirst();

        Explanation explanation = MoveExplainer.explain(StrategyEnum.NAKED_SINGLE, move, state);

        assertEquals("Every other digit is already used up in this cell's row, column or box (a 1 or 5 only once there are two), "
                + "so it can only be a 3.", explanation.text());
        assertEquals(List.of(new BoardPosition(0, 0)), explanation.pattern());
        assertFalse(explanation.blockers().isEmpty());
    }

    @Test
    void refusesAMoveWithoutAReason() {
        Move bare = Move.placement(new BoardPosition(0, 0), 3);
        assertThrows(IllegalArgumentException.class,
                () -> MoveExplainer.explain(StrategyEnum.NAKED_SINGLE, bare, board(new int[]{0, 0})));
    }

    /**
     * Every move every strategy finds on a few hundred boards can be explained, and what the
     * explanation points at is really on the board: a single's other cells can't take the digit,
     * a subset's cells hold what it says, and the eliminations are the move's.
     */
    @Test
    void everyMoveFoundCanBeExplainedAndPointsAtTheRightCells() {
        Map<StrategyEnum, Integer> explained = new EnumMap<>(StrategyEnum.class);
        List<String> problems = new ArrayList<>();

        for (long seed = 0; seed < 300; seed++) {
            int[][] solution = fullSolution(seed);
            SolverSearchState state = SolverSearchState.from(RULES, punchHoles(solution, 50 + (int) (seed % 15), seed));
            while (!SearchUtils.isSolved(state)) {
                for (StrategyEnum strategy : StrategyEnum.values()) {
                    for (Move move : strategy.getStrategy().findAll(state)) {
                        Explanation explanation = MoveExplainer.explain(strategy, move, state);
                        explained.merge(strategy, 1, Integer::sum);
                        String problem = check(strategy, move, explanation, state);
                        if (problem != null) problems.add(strategy + " on seed " + seed + ": " + problem);
                    }
                }
                var next = HumanPidokuSolver.nextMove(state);
                if (next.isEmpty()) break;
                next.get().move().applyTo(state);
            }
        }

        System.out.println("Moves explained by strategy: " + explained);
        for (StrategyEnum strategy : List.of(StrategyEnum.HIDDEN_SINGLE_BOX, StrategyEnum.HIDDEN_SINGLE_LINE,
                StrategyEnum.NAKED_SINGLE, StrategyEnum.NAKED_PAIR, StrategyEnum.INTERSECTION_REMOVAL, StrategyEnum.HIDDEN_PAIR)) {
            assertTrue(explained.getOrDefault(strategy, 0) > 0, strategy + " was never explained");
        }
        assertTrue(problems.isEmpty(), problems.size() + " wrong explanations:\n  " + String.join("\n  ", problems.subList(0, Math.min(20, problems.size()))));
    }

    /** What's wrong with an explanation, or null if nothing is. */
    private static String check(StrategyEnum strategy, Move move, Explanation e, SolverSearchState state) {
        if (e.text() == null || e.text().isBlank() || !e.text().endsWith(".")) return "no sentence: " + e.text();
        if (e.text().contains("null") || e.text().contains("needs no ") || e.text().contains(" 0")) return "odd sentence: " + e.text();
        if (e.units().isEmpty() || e.pattern().isEmpty()) return "nothing to highlight";
        for (BoardPosition blocker : e.blockers()) {
            int held = state.getValue(blocker);
            // A hidden single's blockers are copies of its digit; a naked single's, of the others.
            boolean right = strategy == StrategyEnum.NAKED_SINGLE ? held != PidokuRules.EMPTY && held != e.digit() : held == e.digit();
            if (!right) return "blocker " + blocker + " holds " + held;
        }
        if (move.isPlacement()) {
            if (!move.getPosition().equals(e.cell()) || move.getValue() != e.digit()) return "placement differs";
            if (!e.pattern().contains(e.cell())) return "the cell isn't in the pattern";
            if (!e.eliminations().isEmpty()) return "a placement with eliminations";
        } else {
            if (e.cell() != null || !e.eliminations().equals(move.getEliminations())) return "eliminations differ";
        }
        switch (strategy) {
            case HIDDEN_SINGLE_BOX, HIDDEN_SINGLE_LINE -> {
                Unit unit = e.units().getFirst();
                for (BoardPosition cell : unit.cells(RULES)) {
                    boolean candidate = SearchUtils.isCandidate(state, cell, e.digit());
                    if (candidate != e.pattern().contains(cell)) return "pattern isn't every spot for " + e.digit() + " in " + unit;
                }
                if (MoveExplainer.remaining(state, RULES, unit, e.digit()) != e.pattern().size()) return "spots don't match what's needed";
            }
            case NAKED_PAIR, NAKED_TRIPLE, NAKED_QUAD -> {
                List<Integer> digits = move.getReason().digits();
                for (BoardPosition cell : e.pattern()) {
                    if (!digits.containsAll(SearchUtils.getCandidates(state, cell.getRow(), cell.getCol()))) return "cell " + cell + " has other candidates";
                }
            }
            case HIDDEN_PAIR, HIDDEN_TRIPLE, HIDDEN_QUAD -> {
                Unit unit = e.units().getFirst();
                for (int digit : move.getReason().digits()) {
                    for (BoardPosition cell : unit.cells(RULES)) {
                        if (SearchUtils.isCandidate(state, cell, digit) && !e.pattern().contains(cell)) return digit + " can also go at " + cell;
                    }
                }
            }
            case INTERSECTION_REMOVAL -> {
                for (BoardPosition cell : e.pattern()) {
                    if (!e.units().get(0).contains(cell, RULES) || !e.units().get(1).contains(cell, RULES)) return cell + " isn't where the units cross";
                }
            }
            case X_WING -> {
                if (e.pattern().size() != 4) return "an X-Wing needs four corners";
            }
            case W_WING -> {
                if (e.pattern().size() != 4) return "a W-Wing needs two cells and a link";
            }
            case NAKED_SINGLE -> {
                assertNotNull(e.cell());
            }
        }
        return null;
    }
}
