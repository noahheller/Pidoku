package dev.noahheller.pidoku.pidokuapi.stats;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;

import java.util.List;

/**
 * The signed-in player's own stats: how they do at each difficulty. Built from their first
 * finished attempt at each puzzle, as leaderboards rank it, so replays don't pull the numbers down.
 * Times are ranked times: the game's timer plus the penalties for mistakes and hints.
 *
 * @param source          which puzzles were counted
 * @param includeAssisted whether solves with hints or auto notes were counted
 * @param dailiesSolved   dailies solved, all time, whatever the filters
 * @param difficulties    easiest first; only difficulties with a solve of the chosen source,
 *                        counted or not
 */
public record PlayerStats(StatsSource source, boolean includeAssisted, int currentStreak, int bestStreak,
                          int dailiesSolved, List<DifficultyStats> difficulties) {

    /**
     * One difficulty's numbers. The time fields are null when nothing is counted.
     *
     * @param solved          solves counted
     * @param assisted        solves with hints or auto notes, counted or not (so the page can say
     *                        how many it left out)
     * @param cleanSolves     counted solves with no mistakes and no hints
     * @param averageMistakes over the counted solves; 0 when there are none
     * @param recentSolved    counted solves finished in the last {@link StatsService#RECENT_DAYS} days
     * @param recentAverageMs their average, null when there are none
     */
    public record DifficultyStats(Difficulty difficulty, int solved, int assisted, Long averageMs, Long medianMs,
                                  Long bestMs, int cleanSolves, double averageMistakes, int recentSolved,
                                  Long recentAverageMs) {
    }
}
