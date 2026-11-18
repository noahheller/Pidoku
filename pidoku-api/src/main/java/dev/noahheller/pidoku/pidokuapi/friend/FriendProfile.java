package dev.noahheller.pidoku.pidokuapi.friend;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * What a player sees about one of their friends. Built from the friend's first solve of each
 * daily, the same solves that rank on leaderboards.
 *
 * @param friendsSince  when the two became friends
 * @param currentStreak as of the viewer's date
 * @param dailiesSolved how many daily puzzles they've solved, all time
 * @param cleanSolves   how many of those had no mistakes
 * @param bestTimes     their fastest solve of each daily difficulty they've played, in ms, keyed
 *                      by difficulty id ("easy"…), easiest first
 * @param recent        their solves of the last {@link FriendService#RECENT_DAYS} days' dailies,
 *                      newest date first, easiest first within a date
 */
public record FriendProfile(long id, String displayName, Instant friendsSince,
                            int currentStreak, int bestStreak,
                            int dailiesSolved, int cleanSolves,
                            Map<String, Long> bestTimes,
                            List<RecentSolve> recent) {

    /** One of their recent daily solves. */
    public record RecentSolve(LocalDate date, Difficulty difficulty, long elapsedMs, int mistakes, boolean late) {
    }
}
