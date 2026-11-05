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
        int row = position.getRow(), col = position.getCol();
        if (!state.isEmpty(row, col)) return false;
        if (state.isEliminated(row, col, value)) return false;
        return state.canPlace(position, value);
    }

    /**
     * @param state the current state of the board
     * @param row of the cell
     * @param col of the cell
     * @param value to check
     * @return true if the value is a possible value for the cell. Takes into account soft-eliminations
     */
    public static boolean isCandidate(SolverSearchState state, int row, int col, int value) {
        return isCandidate(state, new BoardPosition(row, col), value);
    }

    /**
     * @param state the current state of the board
     * @param row of the cell
     * @param col of the cell
     * @return List of all possible values for the cell. Takes into account soft-eliminations
     */
    public static List<Integer> getCandidates(SolverSearchState state, int row, int col) {
        List<Integer> result = new ArrayList<>();
        if (!state.isEmpty(row,col)) return result;
        for (int v : state.getRules().getUniqueDigits()) {
            if (isCandidate(state, row, col, v)) result.add(v);
        }
        return result;
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
        BoardPosition bestCell = null;
        int size = state.getSize();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (!state.isEmpty(row, col)) {
                    continue;
                }
                BoardPosition position = new BoardPosition(row, col);
                int candidateCount = SearchUtils.getCandidates(state, row, col).size();
                if (candidateCount == 0) {
                    return position;
                }
                if (candidateCount < bestCount) {
                    bestCount = candidateCount;
                    bestCell = position;
                    if (bestCount == 1) {
                        return bestCell;
                    }
                }
            }
        }
        return bestCell;
    }
}
