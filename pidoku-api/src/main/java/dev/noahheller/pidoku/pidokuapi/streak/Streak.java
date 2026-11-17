package dev.noahheller.pidoku.pidokuapi.streak;

import java.time.LocalDate;
import java.util.List;

/**
 * A player's run of consecutive dates with a solved daily puzzle.
 *
 * @param current     the run that's still alive: ending today, or yesterday if today has no solve
 *                    yet (it ends at the player's next midnight unless they solve one). 0 if neither
 *                    today nor yesterday has a solve.
 * @param best        the longest run ever, at least {@code current}
 * @param solvedToday whether today already counts, so the frontend can say the streak is safe
 */
public record Streak(int current, int best, boolean solvedToday) {

    /**
     * Computes the streak from the dates a player solved a daily on.
     *
     * @param solvedDates distinct dates, earliest first
     * @param today       the player's local date
     */
    public static Streak of(List<LocalDate> solvedDates, LocalDate today) {
        int best = 0;
        int run = 0;
        LocalDate previous = null;
        // The run ending at the latest solved date that isn't after today.
        int runToToday = 0;
        LocalDate lastUpToToday = null;
        for (LocalDate date : solvedDates) {
            run = previous != null && date.equals(previous.plusDays(1)) ? run + 1 : 1;
            best = Math.max(best, run);
            if (!date.isAfter(today)) {
                runToToday = run;
                lastUpToToday = date;
            }
            previous = date;
        }
        boolean solvedToday = today.equals(lastUpToToday);
        boolean alive = solvedToday || today.minusDays(1).equals(lastUpToToday);
        return new Streak(alive ? runToToday : 0, best, solvedToday);
    }
}
