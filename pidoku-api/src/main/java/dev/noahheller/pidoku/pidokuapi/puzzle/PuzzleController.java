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
     * player has already started are skipped while there are others.
     */
    @GetMapping("/random")
    public PuzzleResponse random(@RequestParam Difficulty difficulty, @AuthenticationPrincipal Jwt token) {
        Puzzle puzzle = playerService.find(token)
                .map(player -> puzzleService.randomFromPool(difficulty, player))
                .orElseGet(() -> puzzleService.randomFromPool(difficulty));
        return PuzzleResponse.from(puzzle);
    }

    /** The puzzle behind a share link. */
    @GetMapping("/{shareCode}")
    public PuzzleResponse byShareCode(@PathVariable String shareCode) {
        return PuzzleResponse.from(puzzleService.byShareCode(shareCode));
    }
}
