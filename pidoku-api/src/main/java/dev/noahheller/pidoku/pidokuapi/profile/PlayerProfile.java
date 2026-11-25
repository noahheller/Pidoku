package dev.noahheller.pidoku.pidokuapi.profile;

import dev.noahheller.pidoku.pidokuapi.player.ProfileVisibility;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * What a player sees on someone's profile, or their own. Built from the player's first solve of
 * each daily, the same solves that rank on leaderboards.
 *
 * @param you           this is the viewer's own profile
 * @param visibility    who can see this profile
 * @param friendsSince  when the viewer and this player became friends; null if they aren't
 * @param currentStreak as of the viewer's date
 * @param dailiesSolved how many daily puzzles they've solved, all time
 * @param cleanSolves   how many of those had no mistakes and no hints
 * @param bestTimes     their fastest solve of each daily difficulty they've played, in ms with the
 *                      leaderboards' penalties included, keyed
 *                      by difficulty id ("easy"…), easiest first
 * @param recent        their solves of the last {@link ProfileService#RECENT_DAYS} days' dailies,
 *                      newest date first, easiest first within a date
 */
public record PlayerProfile(long id, String displayName, boolean you, ProfileVisibility visibility,
                            Instant friendsSince,
                            int currentStreak, int bestStreak,
                            int dailiesSolved, int cleanSolves,
                            Map<String, Long> bestTimes,
                            List<RecentSolve> recent) {

    /**
     * One of their recent daily solves.
     *
     * @param elapsedMs the game's time
     * @param penaltyMs added for mistakes and hints on the leaderboards
     */
    public record RecentSolve(LocalDate date, Difficulty difficulty, long elapsedMs, long penaltyMs, int mistakes,
                              int hintsUsed, boolean late) {
    }
}
