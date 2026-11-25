package dev.noahheller.pidoku.pidokuapi.leaderboard;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;

import java.time.LocalDate;
import java.util.List;

/**
 * The fastest solves of one daily puzzle.
 *
 * @param friendsOnly whether only the viewer and their friends are ranked
 * @param cleanOnly   whether only solves with no mistakes are ranked
 * @param total       how many players are ranked, including any beyond {@code entries}
 * @param entries     the fastest, best first, at most {@link LeaderboardService#TOP} of them
 * @param you          the viewer's own entry, also when it's beyond {@code entries}; null if they
 *                     haven't solved it (or aren't signed in)
 * @param fastestClean the fastest solve with no mistakes, also when beyond {@code entries}; null
 *                     if every solve had mistakes
 */
public record Leaderboard(LocalDate date, Difficulty difficulty, boolean friendsOnly, boolean cleanOnly, int total,
                          List<Entry> entries, Entry you, Entry fastestClean) {

    /**
     * One player's ranked solve: their first finished attempt at the puzzle.
     *
     * @param rank        1 for the fastest; equal times share a rank
     * @param displayName null if the player hasn't picked one
     * @param profileId   the player's id, for {@code GET /api/players/{id}/profile}, if the viewer may
     *                    see their profile (it's theirs, it's public, or a friend's shown to friends);
     *                    null otherwise
     * @param elapsedMs   the ranked solve time: the game's timer, or the server's for solves without one
     * @param late        the solve was sent late, so its time is partly the client's claim
     * @param you          this is the viewer's own solve
     * @param fastestClean the fastest solve with no mistakes on this board (ties all count)
     */
    public record Entry(int rank, String displayName, Long profileId, long elapsedMs, int mistakes, boolean late, boolean you,
                        boolean fastestClean) {
    }
}
