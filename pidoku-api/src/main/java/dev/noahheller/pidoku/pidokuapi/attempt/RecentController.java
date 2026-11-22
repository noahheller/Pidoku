package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.player.PlayerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The player's recent puzzles. Needs a Firebase ID token (it's under /api/players). */
@RestController
public class RecentController {

    private final RecentService recentService;
    private final PlayerService playerService;

    public RecentController(RecentService recentService, PlayerService playerService) {
        this.recentService = recentService;
        this.playerService = playerService;
    }

    /**
     * The last {@value RecentService#LIMIT} puzzles the player played, unfinished first ("Continue"),
     * then the latest solves with their times. Dailies are included.
     */
    @GetMapping("/api/players/me/recent")
    public List<RecentPuzzle> recent(@AuthenticationPrincipal Jwt token) {
        return recentService.recent(playerService.forToken(token));
    }
}
