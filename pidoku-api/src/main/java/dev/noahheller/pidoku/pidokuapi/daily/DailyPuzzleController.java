package dev.noahheller.pidoku.pidokuapi.daily;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DailyPuzzleController {

    private final DailyPuzzleService dailyPuzzleService;

    public DailyPuzzleController(DailyPuzzleService dailyPuzzleService) {
        this.dailyPuzzleService = dailyPuzzleService;
    }

    /** Today's (UTC) daily puzzles: one per daily difficulty, easiest first. */
    @GetMapping("/api/puzzles/daily")
    public DailyPuzzlesResponse today() {
        List<DailyPuzzle> dailies = dailyPuzzleService.forToday();
        return DailyPuzzlesResponse.from(dailies.getFirst().getDate(), dailies);
    }
}
