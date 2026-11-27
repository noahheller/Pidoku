package dev.noahheller.pidokuengine.searching;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.strategy.Unit;

import java.util.ArrayList;
import java.util.List;

public final class SearchUtils {

    private SearchUtils() {
    }

    /** The unit's empty cells, in reading order. */
    public static List<BoardPosition> emptyCells(SolverSearchState state, Unit unit) {
        return cellsWhere(state, unit, -1);
    }

    /** The unit's cells that can still hold {@code digit}, in reading order. */
    public static List<BoardPosition> candidateCells(SolverSearchState state, Unit unit, int digit) {
        return cellsWhere(state, unit, digit);
    }

    /** The unit's cells that are empty ({@code digit < 0}) or can hold {@code digit}, in reading order. */
    private static List<BoardPosition> cellsWhere(SolverSearchState state, Unit unit, int digit) {
        PidokuRules rules = state.getRules();
        List<BoardPosition> cells = new ArrayList<>();
        int top = unit.top(rules);
        int left = unit.left(rules);
        int bottom = top + unit.height(rules);
        int right = left + unit.width(rules);
        for (int row = top; row < bottom; row++) {
            for (int col = left; col < right; col++) {
                if (digit < 0 ? state.isEmpty(row, col) : isCandidate(state, row, col, digit)) {
                    cells.add(new BoardPosition(row, col));
                }
            }
        }
        return cells;
    }

    /** How many more copies of {@code digit} the unit needs: as many as π has, less those placed. */
    public static int remaining(SolverSearchState state, Unit unit, int digit) {
        int placed = switch (unit.type()) {
            case ROW -> state.getRowDigitCount(unit.index(), digit);
            case COLUMN -> state.getColDigitCount(unit.index(), digit);
            case BOX -> state.getBoxDigitCount(unit.index(), digit);
        };
        return state.getRules().getDigitFrequency(digit) - placed;
    }

    /**
     * @param state the current state of the board
     * @param position of the cell
     * @param value to check
     * @return true if the value is a possible value for the cell. Takes into account soft-eliminations
     */
    public static boolean isCandidate(SolverSearchState state, BoardPosition position, int value) {
        return isCandidate(state, position.getRow(), position.getCol(), value);
    }

    /**
     * @param state the current state of the board
     * @param row of the cell
     * @param col of the cell
     * @param value to check
     * @return true if the value is a possible value for the cell. Takes into account soft-eliminations
     */
    public static boolean isCandidate(SolverSearchState state, int row, int col, int value) {
        // Filled cells and digits the rules don't have never have a bit set.
        return value >= 0 && value < Integer.SIZE && (state.candidateMask(row, col) & (1 << value)) != 0;
    }

    /**
     * @param state the current state of the board
     * @param row of the cell
     * @param col of the cell
     * @return List of all possible values for the cell, in the rules' digit order (not sorted).
     * Takes into account soft-eliminations
     */
    public static List<Integer> getCandidates(SolverSearchState state, int row, int col) {
        int mask = state.candidateMask(row, col);
        List<Integer> result = new ArrayList<>(Integer.bitCount(mask));
        if (mask == 0) return result;
        for (int v : state.uniqueDigits()) {
            if ((mask & (1 << v)) != 0) result.add(v);
        }
        return result;
    }

    public static int countEmpty(SolverSearchState state) {
        int size = state.getSize();
        int empty = 0;
        for (int row = 0; row < size; row++)
            for (int col = 0; col < size; col++)
                if (state.isEmpty(row, col)) {
                    empty++;
                }
        return empty;
    }

    public static boolean isSolved(SolverSearchState state) {
        int size = state.getSize();
        for (int row = 0; row < size; row++)
            for (int col = 0; col < size; col++)
                if (state.isEmpty(row, col)) {
                    return false;
                }
        return true;
    }

    /**
     * @param state the current state of the board
     * @return the BoardPosition with the lowest number of possible values. Takes into account soft-eliminations
     */
    public static BoardPosition findNextCell(SolverSearchState state) {
        int bestCount = Integer.MAX_VALUE;
        int bestRow = -1;
        int bestCol = -1;
        int size = state.getSize();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (!state.isEmpty(row, col)) {
                    continue;
                }
                // Not the shared cache: the backtracker changes the board between every call, so
                // a full rebuild would be thrown away each time and would lose the early exits below.
                int candidateCount = Integer.bitCount(state.computeCandidateMask(row, col));
                if (candidateCount == 0) {
                    return new BoardPosition(row, col);
                }
                if (candidateCount < bestCount) {
                    bestCount = candidateCount;
                    bestRow = row;
                    bestCol = col;
                    if (bestCount == 1) {
                        return new BoardPosition(row, col);
                    }
                }
            }
        }
        return bestRow < 0 ? null : new BoardPosition(bestRow, bestCol);
    }
}
