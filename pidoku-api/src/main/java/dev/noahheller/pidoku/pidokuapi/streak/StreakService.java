package dev.noahheller.pidoku.pidokuapi.streak;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.player.Player;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Streaks, computed from finished attempts at daily puzzles rather than stored, so there's nothing
 * to keep in step. A date counts if the player solved any of its dailies (an easy one is enough),
 * and runs count consecutive dates.
 *
 * <p>A solve counts for its date only if it happened while that date was "today" somewhere in the
 * world: from its midnight at UTC+14 to its midnight at UTC-12, the window in which the API serves
 * the date's dailies (see {@link DailyPuzzleService#isCurrentSomewhere}). So a missed day can't be
 * filled in later through an old daily's share link, and an attempt finished before the puzzle
 * became a daily (it came from the random pool) doesn't count. Late solves count at the time they
 * were solved, not when they were sent.
 */
@Service
public class StreakService {

    private final JdbcClient jdbc;
    private final DailyPuzzleService dailyPuzzleService;

    public StreakService(JdbcClient jdbc, DailyPuzzleService dailyPuzzleService) {
        this.jdbc = jdbc;
        this.dailyPuzzleService = dailyPuzzleService;
    }

    /**
     * @param today the player's local date
     * @throws InvalidTodayException if {@code today} isn't the date anywhere in the world right now
     */
    @Transactional(readOnly = true)
    public Streak forPlayer(Player player, LocalDate today) {
        if (!dailyPuzzleService.isCurrentSomewhere(today)) {
            throw new InvalidTodayException(today);
        }
        return Streak.of(solvedDates(player), today);
    }

    /** Distinct dates the player solved a daily on, earliest first. */
    List<LocalDate> solvedDates(Player player) {
        return jdbc.sql("""
                        SELECT DISTINCT d.puzzle_date
                        FROM attempts a
                        JOIN daily_puzzles d ON d.puzzle_id = a.puzzle_id
                        WHERE a.player_id = :playerId
                          AND a.completed_at IS NOT NULL
                          AND a.completed_at >= (d.puzzle_date - interval '14 hours') AT TIME ZONE 'UTC'
                          AND a.completed_at <  (d.puzzle_date + interval '36 hours') AT TIME ZONE 'UTC'
                        ORDER BY d.puzzle_date
                        """)
                .param("playerId", player.getId())
                .query(LocalDate.class)
                .list();
    }
}
