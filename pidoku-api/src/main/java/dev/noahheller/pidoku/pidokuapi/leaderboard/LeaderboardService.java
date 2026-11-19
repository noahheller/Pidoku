package dev.noahheller.pidoku.pidokuapi.leaderboard;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzle;
import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Daily leaderboards, computed from attempts on every request (like streaks, nothing is stored).
 *
 * <p>Each player is ranked by their first finished attempt at the daily, timed by the game's own
 * clock ({@code active_ms}, paused while the tab is hidden), or by the server's wall-clock
 * {@code elapsed_ms} for solves recorded without one; replays are recorded but don't rank. Late solves rank too, marked as late.
 * A player who started the puzzle before its date began anywhere (it was a pool puzzle before
 * migration V5) isn't ranked, since they could have seen it in advance. Solving an old daily later,
 * through a share link, does rank: the board is about the puzzle, not the day (unlike streaks).
 *
 * <p>Two rankings: fastest overall, where mistakes don't matter, and clean, which ranks only
 * solves with no mistakes.
 */
@Service
public class LeaderboardService {

    /** How many entries a leaderboard lists. */
    public static final int TOP = 50;

    private final JdbcClient jdbc;
    private final DailyPuzzleService dailyPuzzleService;

    public LeaderboardService(JdbcClient jdbc, DailyPuzzleService dailyPuzzleService) {
        this.jdbc = jdbc;
        this.dailyPuzzleService = dailyPuzzleService;
    }

    /**
     * @param viewer      the signed-in player, whose own entry is marked; empty when signed out
     * @param friendsOnly rank only the viewer and their friends (needs a viewer)
     * @param cleanOnly   rank only solves with no mistakes. Still each player's first solve: a
     *                    clean replay after a solve with mistakes doesn't count.
     * @throws PuzzleNotFoundException if the slot has no daily, or its date hasn't begun anywhere
     */
    @Transactional(readOnly = true)
    public Leaderboard forDaily(DailySlot slot, Optional<Player> viewer, boolean friendsOnly, boolean cleanOnly) {
        DailyPuzzle daily = dailyPuzzleService.inSlot(slot)
                .orElseThrow(() -> new PuzzleNotFoundException(
                        "There's no " + slot.difficulty().id() + " daily puzzle for " + slot.date() + "."));
        long viewerId = viewer.map(Player::getId).orElse(-1L);
        List<Row> rows = jdbc.sql("""
                        WITH firsts AS (
                            SELECT DISTINCT ON (a.player_id)
                                   a.player_id, a.started_at, a.completed_at,
                                   -- The ranked time; it goes out as elapsedMs.
                                   COALESCE(a.active_ms, a.elapsed_ms) AS elapsed_ms, a.mistakes, a.late
                            FROM attempts a
                            WHERE a.puzzle_id = :puzzleId AND a.completed_at IS NOT NULL
                            ORDER BY a.player_id, a.completed_at, a.id
                        ),
                        ranked AS (
                            SELECT f.player_id, p.display_name, f.elapsed_ms, f.mistakes, f.late,
                                   -- Late solves' completion times are the client's claim, so they
                                   -- can't be first to finish.
                                   COALESCE(NOT f.late AND f.completed_at
                                            = MIN(f.completed_at) FILTER (WHERE NOT f.late) OVER (), false) AS first_to_finish,
                                   COALESCE(f.mistakes = 0 AND f.elapsed_ms
                                            = MIN(f.elapsed_ms) FILTER (WHERE f.mistakes = 0) OVER (), false) AS fastest_clean,
                                   RANK() OVER (ORDER BY f.elapsed_ms) AS rank,
                                   ROW_NUMBER() OVER (ORDER BY f.elapsed_ms, f.completed_at, f.player_id) AS position,
                                   COUNT(*) OVER () AS total
                            FROM firsts f
                            JOIN players p ON p.id = f.player_id
                            WHERE f.started_at >= :opensAt
                        """ + (cleanOnly ? """
                              AND f.mistakes = 0
                        """ : "") + (friendsOnly ? """
                              AND (f.player_id = :viewerId
                                   OR f.player_id IN (SELECT friend_id FROM friendships WHERE player_id = :viewerId))
                        """ : "") + """
                        )
                        SELECT * FROM ranked
                        WHERE position <= :top OR player_id = :viewerId OR first_to_finish OR fastest_clean
                        ORDER BY position
                        """)
                .param("puzzleId", daily.getPuzzle().getId())
                .param("opensAt", OffsetDateTime.ofInstant(DailyPuzzleService.opensAt(slot.date()), ZoneOffset.UTC))
                .param("viewerId", viewerId)
                .param("top", TOP)
                .query(Row.class)
                .list();

        List<Leaderboard.Entry> entries = rows.stream()
                .filter(row -> row.position() <= TOP)
                .map(row -> row.toEntry(viewerId))
                .toList();
        Leaderboard.Entry you = rows.stream()
                .filter(row -> row.playerId() == viewerId)
                .findFirst()
                .map(row -> row.toEntry(viewerId))
                .orElse(null);
        Leaderboard.Entry firstToFinish = rows.stream().filter(Row::firstToFinish).findFirst()
                .map(row -> row.toEntry(viewerId)).orElse(null);
        Leaderboard.Entry fastestClean = rows.stream().filter(Row::fastestClean).findFirst()
                .map(row -> row.toEntry(viewerId)).orElse(null);
        int total = rows.isEmpty() ? 0 : (int) rows.getFirst().total();
        return new Leaderboard(slot.date(), slot.difficulty(), friendsOnly, cleanOnly, total, entries, you, firstToFinish, fastestClean);
    }

    /** One row of the ranking query; JdbcClient maps the snake_case columns onto it. */
    record Row(long playerId, String displayName, long elapsedMs, int mistakes, boolean late,
               boolean firstToFinish, boolean fastestClean, long rank, long position, long total) {

        Leaderboard.Entry toEntry(long viewerId) {
            return new Leaderboard.Entry((int) rank, displayName, elapsedMs, mistakes, late, playerId == viewerId,
                    firstToFinish, fastestClean);
        }
    }
}
