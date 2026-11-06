package dev.noahheller.pidokuengine.searching;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.utils.ArrayUtils;

public class SolverSearchState {
    private final int[][] grid;
    private final int size;
    private final int[][] rowDigitCounts;
    private final int[][] colDigitCounts;
    private final int[][] boxDigitCounts;
    private final int[] rowFilled;
    private final int[] colFilled;
    private final int[] boxFilled;
    private final PidokuRules rules;
    private final int boxSize;
    private boolean valid = true;

    /* -----------------------------------------------------------------------
     eliminatedCandidates[row][col] is a bitmask of digit values that have
     been ruled out by human strategies (pairs, triples, etc.) without a
     cell being placed.
     Bit layout: bit (1 << value) is set when `value` is eliminated.
     -----------------------------------------------------------------------
     */
    private final int[][] eliminatedCandidates;

    public static SolverSearchState from(PidokuRules rules, int[][] puzzle) {
        SolverSearchState state = new SolverSearchState(rules, puzzle);
        state.loadInitialValues();
        return state;
    }

    private SolverSearchState(PidokuRules rules, int[][] grid) {
        validateShape(rules, grid);
        this.grid = ArrayUtils.copy(grid);
        this.size = grid.length;
        this.rules = rules;
        rules.getUniqueDigits();
        this.boxSize = rules.boxDim(size);

        int totalBoxes = rules.totalBoxes(size);
        this.rowDigitCounts = new int[size][rules.MAX_VALUE + 1];
        this.colDigitCounts = new int[size][rules.MAX_VALUE + 1];
        this.boxDigitCounts = new int[size][rules.MAX_VALUE + 1];
        this.rowFilled = new int[size];
        this.colFilled = new int[size];
        this.boxFilled = new int[totalBoxes];
        this.eliminatedCandidates = new int[size][size];
    }

    /**
     * Rejects grids that are null, ragged, or a different size than the rules expect.
     */
    private static void validateShape(PidokuRules rules, int[][] grid) {
        int size = rules.getSize();
        if (grid == null || grid.length != size) {
            throw new IllegalArgumentException("grid must have " + size + " rows");
        }
        for (int row = 0; row < size; row++) {
            if (grid[row] == null || grid[row].length != size) {
                throw new IllegalArgumentException("row " + row + " must have " + size + " columns");
            }
        }
    }

    public void eliminate(int row, int col, int value) {
        if (grid[row][col] != PidokuRules.EMPTY) return;
        eliminatedCandidates[row][col] |= (1 << value);
    }

    /**
     * True if {@code value} has been soft-eliminated from (row, col).
     */
    public boolean isEliminated(int row, int col, int value) {
        return (eliminatedCandidates[row][col] & (1 << value)) != 0;
    }


    private void loadInitialValues() {
        for (int row = 0; row < size && valid; row++) {
            for (int col = 0; col < size && valid; col++) {
                if (isEmpty(row, col)) {
                    continue;
                }
                int value = grid[row][col];
                if (!rules.isPlayableValue(value)) {
                    valid = false;
                    return;
                }
                if (!canPlace(new BoardPosition(row, col), value)) {
                    valid = false;
                    return;
                }
                place(new BoardPosition(row, col), value);
            }
        }
    }

    public void place(int row, int col, int value) {
        place(new BoardPosition(row, col), value);
    }

    public void place(BoardPosition position, int value) {
        int row = position.getRow();
        int col = position.getCol();
        int boxIndex = rules.boxIndex(row, col);
        grid[row][col] = value;
        rowDigitCounts[row][value]++;
        colDigitCounts[col][value]++;
        boxDigitCounts[boxIndex][value]++;
        rowFilled[row]++;
        colFilled[col]++;
        boxFilled[boxIndex]++;
        eliminatedCandidates[row][col] = 0;
    }

    public boolean isValid() {
        return valid;
    }

    public void remove(BoardPosition position) {
        int row = position.getRow();
        int col = position.getCol();
        int boxIndex = rules.boxIndex(row, col);
        int value = grid[row][col];
        grid[row][col] = PidokuRules.EMPTY;

        rowDigitCounts[row][value]--;
        colDigitCounts[col][value]--;
        boxDigitCounts[boxIndex][value]--;
        rowFilled[row]--;
        colFilled[col]--;
        boxFilled[boxIndex]--;
        eliminatedCandidates[row][col] = 0;
    }


    public int[][] toArray() {
        return ArrayUtils.copy(grid);
    }


    /**
     * Checks if the value can be placed in the position. Ignores soft-eliminations.
     * @param position to check
     * @param value to check if possible to place
     * @return true if the value can be placed in the position.
     */
    public boolean canPlace(BoardPosition position, int value) {
        int row = position.getRow();
        int col = position.getCol();
        int boxIndex = rules.boxIndex(row, col);
        int freq = rules.getDigitFrequency(value);
        if (freq == -1) {
            return false;
        }
        if (rowDigitCounts[row][value] + 1 > freq) {
            return false;
        }
        if (colDigitCounts[col][value] + 1 > freq) {
            return false;
        }
        if (boxDigitCounts[boxIndex][value] + 1 > freq) {
            return false;
        }
        if (rowFilled[row] + 1 == size && !wouldCompleteDigitCounts(rowDigitCounts[row], value)) {
            return false;
        }
        if (colFilled[col] + 1 == size && !wouldCompleteDigitCounts(colDigitCounts[col], value)) {
            return false;
        }
        return boxFilled[boxIndex] + 1 != boxSize * boxSize
                || wouldCompleteDigitCounts(boxDigitCounts[boxIndex], value);
    }

    private boolean wouldCompleteDigitCounts(int[] counts, int value) {
        for (int digit = rules.MIN_VALUE; digit <= rules.MAX_VALUE; digit++) {
            int next = counts[digit] + (digit == value ? 1 : 0);
            if (next != rules.getDigitFrequency(digit)) {
                return false;
            }
        }
        return true;
    }


    public int getSize() {
        return size;
    }

    public int getRowDigitCount(int row, int digit) {
        return rowDigitCounts[row][digit];
    }

    public int getColDigitCount(int col, int digit) {
        return colDigitCounts[col][digit];
    }

    public int getBoxDigitCount(int boxIndex, int digit) {
        return boxDigitCounts[boxIndex][digit];
    }

    public boolean isEmpty(int row, int col) {
        return grid[row][col] == PidokuRules.EMPTY;
    }

    public boolean isEmpty(BoardPosition position) {
        return isEmpty(position.getRow(), position.getCol());
    }

    public PidokuRules getRules() {
        return rules;
    }


}
