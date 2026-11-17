package dev.noahheller.pidoku.pidokuapi.puzzle;

import dev.noahheller.pidoku.pidokuapi.player.PlayerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Puzzles that aren't tied to a date or a campaign level. {@code GET /api/puzzles/daily/{date}} lives in
 * {@code DailyPuzzleController}; Spring matches the literal paths before {@code /{shareCode}}, and
 * share codes are always 8 upper-case characters, so they can't collide.
 */
@RestController
@RequestMapping("/api/puzzles")
public class PuzzleController {

    private final PuzzleService puzzleService;
    private final PlayerService playerService;

    public PuzzleController(PuzzleService puzzleService, PlayerService playerService) {
        this.puzzleService = puzzleService;
        this.playerService = playerService;
    }

    /**
     * A random puzzle from the pool. Sign-in is optional: with a Firebase ID token, puzzles the
     * player has already started are skipped. Once they've started them all, they get the one they
     * started longest ago, with {@code "repeat": true} so the frontend can say so.
     */
    @GetMapping("/random")
    public RandomPuzzleResponse random(@RequestParam Difficulty difficulty, @AuthenticationPrincipal Jwt token) {
        PuzzleService.RandomPick pick = playerService.find(token)
                .map(player -> puzzleService.randomFromPool(difficulty, player))
                .orElseGet(() -> new PuzzleService.RandomPick(puzzleService.randomFromPool(difficulty), false));
        return new RandomPuzzleResponse(PuzzleResponse.from(pick.puzzle()), pick.repeat());
    }

    /** The puzzle behind a share link ({@code /p/{shareCode}} in the frontend). */
    @GetMapping("/{shareCode}")
    public PuzzleResponse byShareCode(@PathVariable String shareCode) {
        Puzzle puzzle = puzzleService.byShareCode(shareCode);
        return PuzzleResponse.from(puzzle, puzzleService.dailyDate(puzzle).orElse(null));
    }
}
