package dev.noahheller.pidoku.pidokuapi.daily;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@RestController
public class DailyPuzzleController {

    /** How long Firebase Hosting's CDN keeps a complete day's dailies. */
    private static final Duration CDN_CACHE = Duration.ofDays(1);

    private final DailyPuzzleService dailyPuzzleService;

    public DailyPuzzleController(DailyPuzzleService dailyPuzzleService) {
        this.dailyPuzzleService = dailyPuzzleService;
    }

    /**
     * One date's daily puzzles, one per daily difficulty, easiest first, e.g.
     * {@code GET /api/puzzles/daily/2026-11-17}. The frontend asks for the player's local date, so
     * a new day starts at each player's own midnight and everyone gets the same puzzles for the
     * same calendar date. Only dates that are today somewhere in the world are served (404
     * otherwise).
     *
     * <p>Once all of a date's slots are filled its response never changes, so it tells Firebase
     * Hosting's CDN (s-maxage) to keep it: most visits are answered by the CDN and never wake
     * Cloud Run or Neon. Browsers (max-age=0) always ask the CDN, so they hold no copy of their
     * own. A date with a slot still missing isn't cached, so the puzzle shows up as soon as the
     * generator job has filled it.
     */
    @GetMapping("/api/puzzles/daily/{date}")
    public ResponseEntity<DailyPuzzlesResponse> forDate(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<DailyPuzzle> dailies = dailyPuzzleService.forDate(date);
        CacheControl cacheControl = dailyPuzzleService.isComplete(dailies)
                ? CacheControl.maxAge(Duration.ZERO).cachePublic().sMaxAge(CDN_CACHE)
                : CacheControl.noStore();
        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .body(DailyPuzzlesResponse.from(date, dailies));
    }
}
