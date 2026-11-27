package dev.noahheller.pidoku.pidokuapi.profile;

import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleResponse;

import java.time.Instant;
import java.util.List;

/**
 * One page of a player's puzzle history: the puzzles they've solved, dailies and random ones,
 * newest solve first. Unfinished puzzles never show.
 *
 * @param entries at most {@link HistoryService#PAGE_SIZE}
 * @param hasMore there are older solves after these (ask again with the offset moved on)
 */
public record PuzzleHistory(List<Entry> entries, boolean hasMore) {

    /**
     * One solved puzzle.
     *
     * @param puzzle the puzzle, with its daily date if it's a daily, so it can be opened straight away
     * @param solve  the player's first solve of it: the one leaderboards rank
     * @param others the first solves of it by the viewer and the viewer's friends whose history the
     *               viewer may see, fastest first; never the player whose history this is
     */
    public record Entry(PuzzleResponse puzzle, Solve solve, List<OtherSolve> others) {
    }

    /**
     * @param elapsedMs the game's time (the server's for solves without one)
     * @param penaltyMs added for mistakes and hints on the leaderboards
     */
    public record Solve(Instant completedAt, long elapsedMs, long penaltyMs, int mistakes, int hintsUsed,
                        boolean autoNotesUsed, boolean late) {
    }

    /**
     * Someone else's first solve of the same puzzle.
     *
     * @param you the viewer's own solve
     */
    public record OtherSolve(long playerId, String displayName, boolean you, long elapsedMs, long penaltyMs,
                             int mistakes, int hintsUsed, boolean late) {
    }
}
