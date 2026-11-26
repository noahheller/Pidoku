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
    private boolean valid = true;

    /* -----------------------------------------------------------------------
     eliminatedCandidates[row][col] is a bitmask of digit values that have
     been ruled out by human strategies (pairs, triples, etc.) without a
     cell being placed.
     Bit layout: bit (1 << value) is set when `value` is eliminated.
     -----------------------------------------------------------------------
     */
    private final int[][] eliminatedCandidates;

    /* -----------------------------------------------------------------------
     The candidates of every cell, shared by every strategy that looks at this
     board. candidateMasks[row * size + col] has bit (1 << value) set when
     `value` is a candidate there: the cell is empty, the digit isn't
     soft-eliminated, and canPlace allows it. Rebuilt lazily on the first read
     after a place or remove; an eliminate only clears its own bit.
     -----------------------------------------------------------------------
     */
    private final int[] candidateMasks;
    private boolean candidatesStale = true;

    /** The rules' digits in their order (3, 1, 4, 5, 9, 2, 6 for 9x9), copied once. */
    private final int[] uniqueDigits;

    /* -----------------------------------------------------------------------
     What canPlace allows, per unit, kept up to date by place and remove: bit
     (1 << value) is set when the unit can still take `value`. That is, the
     unit has fewer copies of it than the rules allow, and, if the unit has
     one empty cell left, `value` is the digit that would complete it. A cell
     can take a value when its row, column and box all allow it.
     -----------------------------------------------------------------------
     */
    private final int[] rowAllowed;
    private final int[] colAllowed;
    private final int[] boxAllowed;
    /** Copies of each digit (0-9) a unit must hold; 0 for digits this Pidoku doesn't use. */
    private final int[] frequency;

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
        this.uniqueDigits = rules.getUniqueDigits();

        int totalBoxes = rules.totalBoxes(size);
        this.rowDigitCounts = new int[size][rules.MAX_VALUE + 1];
        this.colDigitCounts = new int[size][rules.MAX_VALUE + 1];
        this.boxDigitCounts = new int[size][rules.MAX_VALUE + 1];
        this.rowFilled = new int[size];
        this.colFilled = new int[size];
        this.boxFilled = new int[totalBoxes];
        this.eliminatedCandidates = new int[size][size];
        this.candidateMasks = new int[size * size];

        this.frequency = new int[rules.MAX_VALUE + 1];
        for (int digit = 0; digit <= rules.MAX_VALUE; digit++) frequency[digit] = rules.getDigitFrequency(digit);
        this.rowAllowed = new int[size];
        this.colAllowed = new int[size];
        this.boxAllowed = new int[totalBoxes];
        for (int i = 0; i < size; i++) {
            updateRowAllowed(i);
            updateColAllowed(i);
        }
        for (int box = 0; box < totalBoxes; box++) updateBoxAllowed(box);
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
        // Only this cell's candidates change, so a fresh cache stays fresh.
        if (!candidatesStale) candidateMasks[row * size + col] &= ~(1 << value);
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
                if (!canPlace(row, col, value)) {
                    valid = false;
                    return;
                }
                place(row, col, value);
            }
        }
    }

    public void place(BoardPosition position, int value) {
        place(position.getRow(), position.getCol(), value);
    }

    public void place(int row, int col, int value) {
        int boxIndex = rules.boxIndex(row, col);
        grid[row][col] = value;
        rowDigitCounts[row][value]++;
        colDigitCounts[col][value]++;
        boxDigitCounts[boxIndex][value]++;
        rowFilled[row]++;
        colFilled[col]++;
        boxFilled[boxIndex]++;
        eliminatedCandidates[row][col] = 0;
        candidatesStale = true;
        updateRowAllowed(row);
        updateColAllowed(col);
        updateBoxAllowed(boxIndex);
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
        candidatesStale = true;
        updateRowAllowed(row);
        updateColAllowed(col);
        updateBoxAllowed(boxIndex);
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
        return canPlace(position.getRow(), position.getCol(), value);
    }

    /** {@link #canPlace(BoardPosition, int)} without the position object. */
    public boolean canPlace(int row, int col, int value) {
        if (value < 0 || value >= Integer.SIZE) return false;
        return (allowedAt(row, col) & (1 << value)) != 0;
    }

    /** The digits the cell's row, column and box all still allow (ignores soft-eliminations and whether the cell is empty). */
    private int allowedAt(int row, int col) {
        return rowAllowed[row] & colAllowed[col] & boxAllowed[rules.boxIndex(row, col)];
    }

    /**
     * The cell's candidates as a bitmask, bit {@code 1 << value} per candidate digit; 0 for a filled
     * cell. Takes soft-eliminations into account. Computed once per board change and shared by
     * every caller until the next {@code place} or {@code remove}.
     */
    public int candidateMask(int row, int col) {
        if (candidatesStale) rebuildCandidates();
        return candidateMasks[row * size + col];
    }

    /**
     * {@link #candidateMask} worked out from scratch, without reading or filling the cache. For
     * callers that change the board between every read (the backtracker), where the cache would
     * only be rebuilt and thrown away.
     */
    int computeCandidateMask(int row, int col) {
        if (grid[row][col] != PidokuRules.EMPTY) return 0;
        return allowedAt(row, col) & ~eliminatedCandidates[row][col];
    }

    private void rebuildCandidates() {
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                candidateMasks[row * size + col] = computeCandidateMask(row, col);
            }
        }
        candidatesStale = false;
    }

    /** The rules' digits in their order. Shared, not copied: callers must not modify it. */
    int[] uniqueDigits() {
        return uniqueDigits;
    }

    private void updateRowAllowed(int row) {
        rowAllowed[row] = allowedIn(rowDigitCounts[row], rowFilled[row]);
    }

    private void updateColAllowed(int col) {
        colAllowed[col] = allowedIn(colDigitCounts[col], colFilled[col]);
    }

    private void updateBoxAllowed(int box) {
        boxAllowed[box] = allowedIn(boxDigitCounts[box], boxFilled[box]);
    }

    /**
     * The digits a unit with these counts can still take: fewer copies placed than the rules allow,
     * and, when only one cell is left, the one digit that would leave every count exactly at its
     * frequency (none, if no single digit would).
     */
    private int allowedIn(int[] counts, int filled) {
        int allowed = 0;
        for (int digit : uniqueDigits) {
            if (counts[digit] < frequency[digit]) allowed |= 1 << digit;
        }
        if (filled + 1 != size) return allowed;

        int completing = -1;
        for (int digit = rules.MIN_VALUE; digit <= rules.MAX_VALUE; digit++) {
            if (counts[digit] == frequency[digit]) continue;
            // A second digit off its frequency, or one more than a copy short: no digit completes it.
            if (completing != -1 || counts[digit] != frequency[digit] - 1) return 0;
            completing = digit;
        }
        return completing == -1 ? 0 : allowed & (1 << completing);
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

    /** The digit in a cell, or {@link PidokuRules#EMPTY}. */
    public int getValue(BoardPosition position) {
        return grid[position.getRow()][position.getCol()];
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
