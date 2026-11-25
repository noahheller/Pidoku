package dev.noahheller.pidoku.pidokuapi.leaderboard;

import dev.noahheller.pidoku.pidokuapi.daily.DailyProperties;
import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzle;
import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.player.ProfileVisibility;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Daily leaderboards, computed from attempts on every request (like streaks, nothing is stored).
 *
 * <p>Each player is ranked by their first finished attempt at the daily, timed by the game's own
 * clock ({@code active_ms}, paused while the tab is hidden), or by the server's wall-clock
 * {@code elapsed_ms} for solves recorded without one, plus {@code penalty_ms}: 5 s x mistakes²
 * and the hints' penalties (see {@code Penalties}); replays are recorded but don't rank. Late solves rank too, marked as late.
 * A player who started the puzzle before its date began anywhere (it was a pool puzzle before
 * migration V5) isn't ranked, since they could have seen it in advance. Solving an old daily later,
 * through a share link, does rank: the board is about the puzzle, not the day (unlike streaks).
 *
 * <p>Two rankings: fastest overall, with the penalties added, and clean, which ranks only solves
 * with no mistakes and no hints.
 */
@Service
public class LeaderboardService {

    /** How many entries a leaderboard lists. */
    public static final int TOP = 50;

    private final JdbcClient jdbc;
    private final DailyPuzzleService dailyPuzzleService;
    private final List<Difficulty> dailyDifficulties;

    public LeaderboardService(JdbcClient jdbc, DailyPuzzleService dailyPuzzleService, DailyProperties dailyProperties) {
        this.jdbc = jdbc;
        this.dailyPuzzleService = dailyPuzzleService;
        this.dailyDifficulties = dailyProperties.difficulties();
    }

    /**
     * How many have solved each of a date's dailies and the fastest times, overall and among the
     * viewer's friends: one query for all of them. Counted like {@link #forDaily}: each player's
     * first solve, by the game's timer, leaving out solves started before the date began anywhere.
     *
     * @param viewer the signed-in player, for the friends' numbers; empty when signed out
     * @throws PuzzleNotFoundException if the date hasn't begun anywhere
     */
    @Transactional(readOnly = true)
    public DailySummary summary(LocalDate date, Optional<Player> viewer) {
        if (!dailyPuzzleService.hasBegunSomewhere(date)) {
            throw new PuzzleNotFoundException("There are no daily puzzles for " + date + " yet.");
        }
        long viewerId = viewer.map(Player::getId).orElse(-1L);
        List<SummaryRow> rows = jdbc.sql("""
                        WITH dailies AS (
                            SELECT difficulty, puzzle_id FROM daily_puzzles WHERE puzzle_date = :date
                        ),
                        firsts AS (
                            SELECT DISTINCT ON (a.puzzle_id, a.player_id)
                                   a.puzzle_id, a.player_id, a.started_at, a.completed_at,
                                   -- The ranked time, penalties included (see forDaily).
                                   COALESCE(a.active_ms, a.elapsed_ms) + a.penalty_ms AS elapsed_ms
                            FROM attempts a
                            JOIN dailies d ON d.puzzle_id = a.puzzle_id
                            WHERE a.completed_at IS NOT NULL
                            ORDER BY a.puzzle_id, a.player_id, a.completed_at, a.id
                        ),
                        solves AS (
                            SELECT f.puzzle_id, f.player_id, f.elapsed_ms, f.completed_at, p.display_name,
                                   f.player_id = :viewerId AS you,
                                   f.player_id IN (SELECT friend_id FROM friendships WHERE player_id = :viewerId) AS friend
                            FROM firsts f
                            JOIN players p ON p.id = f.player_id
                            WHERE f.started_at >= :opensAt
                        ),
                        fastest AS (
                            SELECT DISTINCT ON (puzzle_id) puzzle_id, display_name, elapsed_ms, you
                            FROM solves
                            ORDER BY puzzle_id, elapsed_ms, completed_at, player_id
                        ),
                        friends_fastest AS (
                            SELECT DISTINCT ON (puzzle_id) puzzle_id, display_name, elapsed_ms, you
                            FROM solves
                            WHERE you OR friend
                            ORDER BY puzzle_id, elapsed_ms, completed_at, player_id
                        ),
                        -- The viewer's own first solve, even one started before the date began.
                        mine AS (
                            SELECT DISTINCT ON (a.puzzle_id)
                                   a.puzzle_id, COALESCE(a.active_ms, a.elapsed_ms) AS elapsed_ms, a.mistakes,
                                   a.hints_used, a.hint_penalty_ms, a.auto_notes_used, a.late
                            FROM attempts a
                            JOIN dailies d ON d.puzzle_id = a.puzzle_id
                            WHERE a.player_id = :viewerId AND a.completed_at IS NOT NULL
                            ORDER BY a.puzzle_id, a.completed_at, a.id
                        )
                        SELECT d.difficulty,
                               (SELECT COUNT(*) FROM solves s WHERE s.puzzle_id = d.puzzle_id) AS solved,
                               (SELECT COUNT(*) FROM solves s WHERE s.puzzle_id = d.puzzle_id AND s.friend) AS friends_solved,
                               f.display_name AS fastest_name, f.elapsed_ms AS fastest_ms, f.you AS fastest_you,
                               ff.display_name AS friends_fastest_name, ff.elapsed_ms AS friends_fastest_ms,
                               ff.you AS friends_fastest_you,
                               m.elapsed_ms AS your_ms, m.mistakes AS your_mistakes, m.hints_used AS your_hints_used,
                               m.hint_penalty_ms AS your_hint_penalty_ms, m.auto_notes_used AS your_auto_notes_used,
                               m.late AS your_late
                        FROM dailies d
                        LEFT JOIN fastest f ON f.puzzle_id = d.puzzle_id
                        LEFT JOIN friends_fastest ff ON ff.puzzle_id = d.puzzle_id
                        LEFT JOIN mine m ON m.puzzle_id = d.puzzle_id
                        """)
                .param("date", date)
                .param("opensAt", OffsetDateTime.ofInstant(DailyPuzzleService.opensAt(date), ZoneOffset.UTC))
                .param("viewerId", viewerId)
                .query(SummaryRow.class)
                .list();

        boolean signedIn = viewer.isPresent();
        List<DailySummary.Daily> dailies = rows.stream()
                .map(row -> row.toDaily(signedIn))
                // Only today's daily difficulties: a date can still hold one since dropped from config (V7).
                .filter(daily -> dailyDifficulties.contains(daily.difficulty()))
                .sorted(Comparator.comparing(DailySummary.Daily::difficulty))
                .toList();
        return new DailySummary(date, dailies);
    }

    /** One daily's row of the summary query. */
    record SummaryRow(String difficulty, long solved, long friendsSolved,
                      String fastestName, Long fastestMs, Boolean fastestYou,
                      String friendsFastestName, Long friendsFastestMs, Boolean friendsFastestYou,
                      Long yourMs, Integer yourMistakes, Integer yourHintsUsed, Long yourHintPenaltyMs,
                      Boolean yourAutoNotesUsed, Boolean yourLate) {

        DailySummary.Daily toDaily(boolean signedIn) {
            DailySummary.Time fastest = fastestMs == null ? null
                    : new DailySummary.Time(fastestName, fastestMs, Boolean.TRUE.equals(fastestYou));
            DailySummary.Time friendsFastest = !signedIn || friendsFastestMs == null ? null
                    : new DailySummary.Time(friendsFastestName, friendsFastestMs, Boolean.TRUE.equals(friendsFastestYou));
            DailySummary.YourSolve yours = !signedIn || yourMs == null ? null
                    : new DailySummary.YourSolve(yourMs, yourMistakes, yourHintsUsed, yourHintPenaltyMs,
                            Boolean.TRUE.equals(yourAutoNotesUsed), Boolean.TRUE.equals(yourLate));
            return new DailySummary.Daily(Difficulty.valueOf(difficulty), (int) solved, fastest,
                    signedIn ? (int) friendsSolved : null, friendsFastest, yours);
        }
    }

    /**
     * @param viewer      the signed-in player, whose own entry is marked; empty when signed out
     * @param friendsOnly rank only the viewer and their friends (needs a viewer)
     * @param cleanOnly   rank only solves with no mistakes and no hints. Still each player's first solve: a
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
                                   -- The game's time (it goes out as elapsedMs) and what's added to it.
                                   COALESCE(a.active_ms, a.elapsed_ms) AS elapsed_ms, a.penalty_ms,
                                   COALESCE(a.active_ms, a.elapsed_ms) + a.penalty_ms AS ranked_ms,
                                   a.mistakes, a.hints_used, a.late
                            FROM attempts a
                            WHERE a.puzzle_id = :puzzleId AND a.completed_at IS NOT NULL
                            ORDER BY a.player_id, a.completed_at, a.id
                        ),
                        ranked AS (
                            SELECT f.player_id, p.display_name, p.profile_visibility, f.elapsed_ms, f.penalty_ms,
                                   f.mistakes, f.hints_used, f.late,
                                   f.player_id IN (SELECT friend_id FROM friendships WHERE player_id = :viewerId) AS friend,
                                   COALESCE(f.mistakes = 0 AND f.hints_used = 0 AND f.ranked_ms
                                            = MIN(f.ranked_ms) FILTER (WHERE f.mistakes = 0 AND f.hints_used = 0) OVER (),
                                            false) AS fastest_clean,
                                   RANK() OVER (ORDER BY f.ranked_ms) AS rank,
                                   ROW_NUMBER() OVER (ORDER BY f.ranked_ms, f.completed_at, f.player_id) AS position,
                                   COUNT(*) OVER () AS total
                            FROM firsts f
                            JOIN players p ON p.id = f.player_id
                            WHERE f.started_at >= :opensAt
                        """ + (cleanOnly ? """
                              AND f.mistakes = 0 AND f.hints_used = 0
                        """ : "") + (friendsOnly ? """
                              AND (f.player_id = :viewerId
                                   OR f.player_id IN (SELECT friend_id FROM friendships WHERE player_id = :viewerId))
                        """ : "") + """
                        )
                        SELECT * FROM ranked
                        WHERE position <= :top OR player_id = :viewerId OR fastest_clean
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
        Leaderboard.Entry fastestClean = rows.stream().filter(Row::fastestClean).findFirst()
                .map(row -> row.toEntry(viewerId)).orElse(null);
        int total = rows.isEmpty() ? 0 : (int) rows.getFirst().total();
        return new Leaderboard(slot.date(), slot.difficulty(), friendsOnly, cleanOnly, total, entries, you, fastestClean);
    }

    /** One row of the ranking query; JdbcClient maps the snake_case columns onto it. */
    record Row(long playerId, String displayName, String profileVisibility, long elapsedMs, long penaltyMs,
               int mistakes, int hintsUsed, boolean late, boolean friend, boolean fastestClean, long rank, long position,
               long total) {

        Leaderboard.Entry toEntry(long viewerId) {
            boolean you = playerId == viewerId;
            // Only the ids of profiles the viewer may open, so private players' ids don't leak.
            Long profileId = ProfileVisibility.fromId(profileVisibility).visibleTo(you, friend) ? playerId : null;
            return new Leaderboard.Entry((int) rank, displayName, profileId, elapsedMs, penaltyMs, mistakes, hintsUsed,
                    late, you, fastestClean);
        }
    }
}
