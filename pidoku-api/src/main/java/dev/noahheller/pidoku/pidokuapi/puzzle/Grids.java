package dev.noahheller.pidoku.pidokuapi.puzzle;

/**
 * Converts between the engine's {@code int[row][col]} grids and the stored form: one character per
 * cell, row-major, with {@code 0} for an empty cell. Pidoku digits are all single decimal digits,
 * so one character per cell works for every board size.
 */
public final class Grids {

    private Grids() {
    }

    /** @throws IllegalArgumentException if the grid isn't square or holds a value outside 0–9 */
    public static String encode(int[][] grid) {
        StringBuilder cells = new StringBuilder(grid.length * grid.length);
        for (int[] row : grid) {
            if (row.length != grid.length) {
                throw new IllegalArgumentException("grid must be square, but a row has "
                        + row.length + " cells and there are " + grid.length + " rows");
            }
            for (int value : row) {
                if (value < 0 || value > 9) {
                    throw new IllegalArgumentException("cell value " + value + " is not a single digit");
                }
                cells.append((char) ('0' + value));
            }
        }
        return cells.toString();
    }

    /** The cells of an encoded grid as a flat, row-major array. */
    public static int[] toCells(String encoded) {
        int[] cells = new int[encoded.length()];
        for (int i = 0; i < cells.length; i++) {
            char c = encoded.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("'" + c + "' at index " + i + " is not a digit");
            }
            cells[i] = c - '0';
        }
        return cells;
    }

    /** Number of non-empty cells in an encoded grid. */
    public static int countClues(String encoded) {
        return (int) encoded.chars().filter(c -> c != '0').count();
    }
}
