package dev.noahheller.pidoku.pidokuapi.daily;

import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * @param date    the UTC date these are the dailies for
 * @param puzzles one per daily difficulty, easiest first; each says its own difficulty
 */
public record DailyPuzzlesResponse(LocalDate date, List<PuzzleResponse> puzzles) {

    /** @param dailies one date's dailies, easiest first (as {@link DailyPuzzleService#forToday()} returns them) */
    public static DailyPuzzlesResponse from(LocalDate date, List<DailyPuzzle> dailies) {
        return new DailyPuzzlesResponse(date, dailies.stream()
                .map(daily -> PuzzleResponse.from(daily.getPuzzle()))
                .toList());
    }
}
