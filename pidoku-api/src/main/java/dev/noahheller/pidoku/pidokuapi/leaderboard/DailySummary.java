package dev.noahheller.pidoku.pidokuapi.leaderboard;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;

import java.time.LocalDate;
import java.util.List;

/**
 * A few numbers about each of one date's dailies, for the cards on the home screen: how many have
 * solved it and the fastest time, overall and among the viewer's friends. Ranked like the
 * leaderboards (each player's first solve, by the game's timer), so the numbers match them.
 *
 * @param dailies one per daily difficulty that has a puzzle on the date, easiest first
 */
public record DailySummary(LocalDate date, List<Daily> dailies) {

    /**
     * @param solved          how many players have solved it
     * @param fastest         the fastest solve; null if no one has solved it
     * @param friendsSolved   how many of the viewer's friends have solved it (not counting the
     *                        viewer); null when signed out
     * @param friendsFastest  the fastest solve by the viewer or one of their friends; null when
     *                        signed out or none of them has solved it
     */
    public record Daily(Difficulty difficulty, int solved, Time fastest, Integer friendsSolved, Time friendsFastest) {
    }

    /**
     * @param displayName null if the player hasn't picked one
     * @param elapsedMs   the ranked time: the game's timer (or the server's for solves without one)
     *                    plus the penalties for mistakes and hints
     * @param you         it's the viewer's own solve
     */
    public record Time(String displayName, long elapsedMs, boolean you) {
    }
}
