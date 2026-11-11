package dev.noahheller.pidoku.pidokuapi.puzzle;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Puzzles that aren't tied to a date or a campaign level. {@code GET /api/puzzles/daily} lives in
 * {@code DailyPuzzleController}; Spring matches the literal paths before {@code /{shareCode}}, and
 * share codes are always 8 upper-case characters, so they can't collide.
 */
@RestController
@RequestMapping("/api/puzzles")
public class PuzzleController {

    private final PuzzleService puzzleService;

    public PuzzleController(PuzzleService puzzleService) {
        this.puzzleService = puzzleService;
    }

    /** A random puzzle from the pool. */
    @GetMapping("/random")
    public PuzzleResponse random(@RequestParam Difficulty difficulty) {
        return PuzzleResponse.from(puzzleService.randomFromPool(difficulty));
    }

    /** The puzzle behind a share link. */
    @GetMapping("/{shareCode}")
    public PuzzleResponse byShareCode(@PathVariable String shareCode) {
        return PuzzleResponse.from(puzzleService.byShareCode(shareCode));
    }
}
