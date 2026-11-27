package dev.noahheller.pidoku.pidokuapi.profile;

import dev.noahheller.pidoku.pidokuapi.attempt.AttemptSql;
import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.player.ProfileVisibility;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.streak.InvalidTodayException;
import dev.noahheller.pidoku.pidokuapi.streak.Streak;
import dev.noahheller.pidoku.pidokuapi.streak.StreakService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Player profiles, as their {@link ProfileVisibility} allows: everyone sees their own, friends see
 * a 'friends' one, and any player sees a 'public' one. Other players' profiles are only shown to
 * players signed in with Google. The puzzle history on a profile has its own visibility (see
 * {@link HistoryService}).
 */
@Service
public class ProfileService {

    /** How many days of dailies a profile lists. */
    public static final int RECENT_DAYS = 7;

    private final JdbcClient jdbc;
    private final StreakService streakService;
    private final ProfileAccess access;

    public ProfileService(JdbcClient jdbc, StreakService streakService, ProfileAccess access) {
        this.jdbc = jdbc;
        this.streakService = streakService;
        this.access = access;
    }

    /**
     * A player's profile: streaks, totals, best times and recent dailies. Two small queries, both
     * on that one player's rows: the player (with any friendship with the viewer), and their first
     * solve of each daily (as ranked on leaderboards: started once the date had begun somewhere).
     *
     * @param viewerUsesGoogle whether the viewer is signed in with Google: only then may they see
     *                         other players' profiles
     * @param today            the viewer's local date, for the streak and what counts as recent
     * @throws GoogleSignInRequiredException if it's someone else's and the viewer is anonymous
     * @throws ProfileNotFoundException if there's no such player, or the viewer may not see them
     * @throws InvalidTodayException    if {@code today} isn't the date anywhere in the world right now
     */
    @Transactional(readOnly = true)
    public PlayerProfile profile(Player viewer, boolean viewerUsesGoogle, long playerId, LocalDate today) {
        ProfileAccess.Target target = access.target(viewer, viewerUsesGoogle, playerId);
        Streak streak = streakService.forPlayer(playerId, today);

        record Solve(LocalDate puzzleDate, String difficulty, long elapsedMs, long penaltyMs, int mistakes,
                     int hintsUsed, boolean late) {
        }
        List<Solve> solves = jdbc.sql("""
                        WITH firsts AS (
                            SELECT DISTINCT ON (a.puzzle_id)
                                   d.puzzle_date, d.difficulty, a.started_at,
                                   %s AS elapsed_ms, a.penalty_ms, a.mistakes,
                                   a.hints_used, a.late
                            FROM attempts a
                            JOIN daily_puzzles d ON d.puzzle_id = a.puzzle_id
                            WHERE a.player_id = :playerId AND a.completed_at IS NOT NULL
                            ORDER BY a.puzzle_id, a.completed_at, a.id
                        )
                        SELECT puzzle_date, difficulty, elapsed_ms, penalty_ms, mistakes, hints_used, late
                        FROM firsts
                        WHERE started_at >= %s
                        ORDER BY puzzle_date DESC
                        """.formatted(AttemptSql.GAME_MS, DailyPuzzleService.opensAtSql("puzzle_date")))
                .param("playerId", playerId)
                .query(Solve.class)
                .list();

        Map<Difficulty, Long> bestTimes = new EnumMap<>(Difficulty.class);
        int clean = 0;
        LocalDate recentFrom = today.minusDays(RECENT_DAYS - 1);
        List<PlayerProfile.RecentSolve> recent = new ArrayList<>();
        for (Solve solve : solves) {
            Difficulty difficulty = Difficulty.valueOf(solve.difficulty());
            // Ranked like the leaderboards: penalties included.
            bestTimes.merge(difficulty, solve.elapsedMs() + solve.penaltyMs(), Math::min);
            if (solve.mistakes() == 0 && solve.hintsUsed() == 0) {
                clean++;
            }
            if (!solve.puzzleDate().isBefore(recentFrom) && !solve.puzzleDate().isAfter(today)) {
                recent.add(new PlayerProfile.RecentSolve(solve.puzzleDate(), difficulty, solve.elapsedMs(),
                        solve.penaltyMs(), solve.mistakes(), solve.hintsUsed(), solve.late()));
            }
        }
        recent.sort(Comparator.comparing(PlayerProfile.RecentSolve::date).reversed()
                .thenComparing(PlayerProfile.RecentSolve::difficulty));
        Map<String, Long> bestById = new LinkedHashMap<>();
        bestTimes.forEach((difficulty, ms) -> bestById.put(difficulty.id(), ms));
        return new PlayerProfile(playerId, target.displayName(), target.you(), target.visibility(),
                target.historyVisibility(), target.historyVisible(), target.friendsSince(),
                streak.current(), streak.best(), solves.size(), clean, bestById, List.copyOf(recent));
    }
}
