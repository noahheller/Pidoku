package dev.noahheller.pidoku.pidokuapi.streak;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public Streak forPlayer(long playerId, LocalDate today) {
        return forPlayers(List.of(playerId), today).get(playerId);
    }

    /**
     * Several players' streaks at once, in one query: for showing friends' streaks. Each is computed
     * as of the viewer's {@code today}, since the server doesn't know the friends' own dates; that
     * can be a day out for a friend in a far-off time zone.
     *
     * @return every player in {@code playerIds}, with a streak of 0 for those who never solved a daily
     * @throws InvalidTodayException if {@code today} isn't the date anywhere in the world right now
     */
    @Transactional(readOnly = true)
    public Map<Long, Streak> forPlayers(Collection<Long> playerIds, LocalDate today) {
        if (!dailyPuzzleService.isCurrentSomewhere(today)) {
            throw new InvalidTodayException(today);
        }
        Map<Long, List<LocalDate>> dates = solvedDatesByPlayer(playerIds);
        Map<Long, Streak> streaks = new HashMap<>();
        for (long id : playerIds) {
            streaks.put(id, Streak.of(dates.getOrDefault(id, List.of()), today));
        }
        return streaks;
    }

    /** Distinct dates each player solved a daily on (see the class comment for what counts), earliest first. */
    private Map<Long, List<LocalDate>> solvedDatesByPlayer(Collection<Long> playerIds) {
        if (playerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<LocalDate>> dates = new HashMap<>();
        jdbc.sql("""
                        SELECT DISTINCT a.player_id, d.puzzle_date
                        FROM attempts a
                        JOIN daily_puzzles d ON d.puzzle_id = a.puzzle_id
                        WHERE a.player_id IN (:playerIds)
                          AND a.completed_at IS NOT NULL
                          AND a.completed_at >= %s
                          AND a.completed_at <  %s
                        ORDER BY a.player_id, d.puzzle_date
                        """.formatted(DailyPuzzleService.opensAtSql("d.puzzle_date"),
                                DailyPuzzleService.closesAtSql("d.puzzle_date")))
                .param("playerIds", playerIds)
                .query((rs, n) -> dates.computeIfAbsent(rs.getLong(1), id -> new ArrayList<>())
                        .add(rs.getObject(2, LocalDate.class)))
                .list();
        return dates;
    }
}
