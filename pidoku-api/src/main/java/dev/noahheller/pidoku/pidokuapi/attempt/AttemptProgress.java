package dev.noahheller.pidoku.pidokuapi.attempt;

/**
 * An unfinished attempt's board, so it follows the player to another device (column
 * {@code attempts.progress}, V9). Grids are flat and row-major like the puzzle's.
 *
 * @param values   the digits in each cell, 0 for empty (givens included)
 * @param notes    each cell's pencil marks as a bitmask: bit {@code 1 << d} for digit d
 * @param mistakes wrong digits entered so far
 * @param activeMs the game's timer so far
 */
public record AttemptProgress(int[] values, int[] notes, int mistakes, long activeMs) {
}
