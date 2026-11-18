package dev.noahheller.pidoku.pidokuapi.leaderboard;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;

import java.time.LocalDate;
import java.util.List;

/**
 * The fastest solves of one daily puzzle.
 *
 * @param friendsOnly whether only the viewer and their friends are ranked
 * @param total       how many players are ranked, including any beyond {@code entries}
 * @param entries     the fastest, best first, at most {@link LeaderboardService#TOP} of them
 * @param you         the viewer's own entry, also when it's beyond {@code entries}; null if they
 *                    haven't solved it (or aren't signed in)
 */
public record Leaderboard(LocalDate date, Difficulty difficulty, boolean friendsOnly, int total,
                          List<Entry> entries, Entry you) {

    /**
     * One player's ranked solve: their first finished attempt at the puzzle.
     *
     * @param rank        1 for the fastest; equal times share a rank
     * @param displayName null if the player hasn't picked one
     * @param elapsedMs   the server-measured solve time
     * @param late        the solve was sent late, so its time is partly the client's claim
     * @param you         this is the viewer's own solve
     */
    public record Entry(int rank, String displayName, long elapsedMs, int mistakes, boolean late, boolean you) {
    }
}
