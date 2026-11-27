package dev.noahheller.pidoku.pidokuapi.stats;

import dev.noahheller.pidoku.pidokuapi.attempt.AttemptSql;
import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.streak.InvalidTodayException;
import dev.noahheller.pidoku.pidokuapi.streak.Streak;
import dev.noahheller.pidoku.pidokuapi.streak.StreakService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The stats page's numbers, computed per request from the player's own attempts (cheap at friend
 * scale: one query over one player's rows, summed up here).
 *
 * <p>Each puzzle counts once, by the player's first finished attempt at it, like the leaderboards.
 * A daily's solve counts only if it was started once the date had begun somewhere (as on the
 * leaderboards and the profile), so a pool puzzle played before it became a daily doesn't.
 */
@Service
public class StatsService {

    /** How far back "recent" goes, for the trend. */
    public static final int RECENT_DAYS = 30;

    private final JdbcClient jdbc;
    private final StreakService streakService;
    private final Clock clock;

    public StatsService(JdbcClient jdbc, StreakService streakService, Clock clock) {
        this.jdbc = jdbc;
        this.streakService = streakService;
        this.clock = clock;
    }

    /** One counted puzzle: the player's first solve of it. */
    record Solve(String difficulty, boolean daily, String source, long rankedMs, int mistakes, int hintsUsed,
                 boolean autoNotesUsed, Instant completedAt) {

        boolean assisted() {
            return hintsUsed > 0 || autoNotesUsed;
        }

        boolean clean() {
            return mistakes == 0 && hintsUsed == 0;
        }

        boolean from(StatsSource wanted) {
            return switch (wanted) {
                case ALL -> true;
                case DAILY -> daily;
                case RANDOM -> !daily && "POOL".equals(source);
            };
        }
    }

    /**
     * @param includeAssisted count solves that used hints or auto notes
     * @param today           the player's local date, for the streak
     * @throws InvalidTodayException if {@code today} isn't the date anywhere in the world right now
     */
    @Transactional(readOnly = true)
    public PlayerStats forPlayer(Player player, StatsSource source, boolean includeAssisted, LocalDate today) {
        Streak streak = streakService.forPlayer(player.getId(), today);
        List<Solve> solves = jdbc.sql("""
                        WITH firsts AS (
                            SELECT DISTINCT ON (a.puzzle_id)
                                   p.difficulty, d.puzzle_date, p.source, a.started_at, a.completed_at,
                                   %s AS ranked_ms,
                                   a.mistakes, a.hints_used, a.auto_notes_used
                            FROM attempts a
                            JOIN puzzles p ON p.id = a.puzzle_id
                            LEFT JOIN daily_puzzles d ON d.puzzle_id = a.puzzle_id
                            WHERE a.player_id = :playerId AND a.completed_at IS NOT NULL
                            ORDER BY a.puzzle_id, a.completed_at, a.id
                        )
                        SELECT difficulty, puzzle_date IS NOT NULL AS daily, source, ranked_ms, mistakes,
                               hints_used, auto_notes_used, completed_at
                        FROM firsts
                        -- A daily counts from when its date began in the earliest time zone (UTC+14).
                        WHERE puzzle_date IS NULL
                           OR started_at >= %s
                        """.formatted(AttemptSql.RANKED_MS, DailyPuzzleService.opensAtSql("puzzle_date")))
                .param("playerId", player.getId())
                .query(Solve.class)
                .list();

        int dailiesSolved = (int) solves.stream().filter(Solve::daily).count();
        Instant recentFrom = clock.instant().minus(Duration.ofDays(RECENT_DAYS));
        Map<Difficulty, List<Solve>> byDifficulty = new EnumMap<>(Difficulty.class);
        for (Solve solve : solves) {
            if (solve.from(source)) {
                byDifficulty.computeIfAbsent(Difficulty.valueOf(solve.difficulty()), d -> new ArrayList<>()).add(solve);
            }
        }
        List<PlayerStats.DifficultyStats> difficulties = new ArrayList<>();
        byDifficulty.forEach((difficulty, all) -> {
            List<Solve> counted = all.stream().filter(s -> includeAssisted || !s.assisted()).toList();
            List<Long> times = counted.stream().map(Solve::rankedMs).sorted().toList();
            List<Long> recent = counted.stream()
                    .filter(s -> !s.completedAt().isBefore(recentFrom))
                    .map(Solve::rankedMs)
                    .toList();
            difficulties.add(new PlayerStats.DifficultyStats(
                    difficulty,
                    counted.size(),
                    (int) all.stream().filter(Solve::assisted).count(),
                    average(times),
                    median(times),
                    times.isEmpty() ? null : times.getFirst(),
                    (int) counted.stream().filter(Solve::clean).count(),
                    counted.stream().mapToInt(Solve::mistakes).average().orElse(0),
                    recent.size(),
                    average(recent)));
        });
        return new PlayerStats(source, includeAssisted, streak.current(), streak.best(), dailiesSolved,
                List.copyOf(difficulties));
    }

    /** Rounded to the millisecond; null for no times. */
    static Long average(List<Long> times) {
        if (times.isEmpty()) return null;
        return Math.round(times.stream().mapToLong(Long::longValue).average().orElseThrow());
    }

    /** The middle time, or the mean of the middle two; null for no times. {@code sorted} must be in order. */
    static Long median(List<Long> sorted) {
        if (sorted.isEmpty()) return null;
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : Math.round((sorted.get(mid - 1) + sorted.get(mid)) / 2.0);
    }
}
