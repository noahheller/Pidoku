package dev.noahheller.pidokuengine.searching;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;

import java.util.ArrayList;
import java.util.List;

public class SearchUtils {
    /**
     * @param state the current state of the board
     * @param row to check
     * @return List of all empty cells in the row
     */
    public static List<BoardPosition> emptyCellsInRow(SolverSearchState state, int row) {
        List<BoardPosition> cells = new ArrayList<>();
        for (int col = 0; col < state.getSize(); col++)
            if (state.isEmpty(row, col)) {
                cells.add(new BoardPosition(row, col));
            }
        return cells;
    }

    /**
     * @param state the current state of the board
     * @param col to check
     * @return List of all empty cells in the col
     */
    public static List<BoardPosition> emptyCellsInCol(SolverSearchState state, int col) {
        List<BoardPosition> cells = new ArrayList<>();
        for (int row = 0; row < state.getSize(); row++)
            if (state.isEmpty(row, col)) {
                cells.add(new BoardPosition(row, col));
            }
        return cells;
    }

    /**
     * @param state the current state of the board
     * @param boxIndex of the box
     * @return List of all empty cells in the box
     */
    public static List<BoardPosition> emptyCellsInBox(SolverSearchState state, int boxIndex) {
        List<BoardPosition> cells = new ArrayList<>();
        PidokuRules rules = state.getRules();
        int size = state.getSize();
        int boxSize = rules.boxDim(size);
        int startRow = (boxIndex / rules.boxesPerRow(size)) * boxSize;
        int startCol = (boxIndex % rules.boxesPerRow(size)) * boxSize;
        for (int row = startRow; row < startRow + boxSize; row++)
            for (int col = startCol; col < startCol + boxSize; col++)
                if (state.isEmpty(row, col)) {
                    cells.add(new BoardPosition(row, col));
                }
        return cells;
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
