package dev.noahheller.pidoku.pidokuapi.attempt;

/** Checks on a board a player sends: progress to save, or the board a hint is asked for. */
final class Boards {

    private Boards() {
    }

    /**
     * The board has the puzzle's size, keeps its givens, and holds only digits and note bits.
     *
     * @param values each cell's digit, 0 for empty
     * @param notes  each cell's notes as a bitmask, or null if none were sent
     * @param givens the puzzle's givens, 0 for empty
     * @throws InvalidProgressException if it doesn't
     */
    static void checkFits(int[] values, int[] notes, int[] givens) {
        if (values.length != givens.length || (notes != null && notes.length != givens.length)) {
            throw new InvalidProgressException("The board must have " + givens.length + " cells.");
        }
        for (int i = 0; i < givens.length; i++) {
            if (values[i] < 0 || values[i] > 9 || (givens[i] != 0 && values[i] != givens[i])) {
                throw new InvalidProgressException("Cell " + i + " doesn't fit the puzzle.");
            }
            if (notes != null && (notes[i] < 0 || notes[i] >= 1 << 10)) {
                throw new InvalidProgressException("Cell " + i + " has notes that aren't digits.");
            }
        }
    }
}
