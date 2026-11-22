package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleResponse;

import java.time.Instant;

/**
 * One of the player's recent puzzles, for the Recent list on the home screen.
 *
 * @param puzzle       the puzzle, with its daily date if it's a daily
 * @param completed    false while the player has an unfinished attempt at it ("Continue")
 * @param timeMs       for a solve, its time (the game's timer, else the server's); for an
 *                     unfinished one, the timer as last saved; null if not known
 * @param mistakes     on the attempt shown
 * @param lastPlayedAt when the player last started, saved or solved it
 */
public record RecentPuzzle(PuzzleResponse puzzle, boolean completed, Long timeMs, int mistakes, Instant lastPlayedAt) {
}
