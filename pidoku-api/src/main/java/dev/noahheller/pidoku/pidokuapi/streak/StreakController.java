package dev.noahheller.pidoku.pidokuapi.streak;

import dev.noahheller.pidoku.pidokuapi.player.PlayerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class StreakController {

    private final StreakService streakService;
    private final PlayerService playerService;

    public StreakController(StreakService streakService, PlayerService playerService) {
        this.streakService = streakService;
        this.playerService = playerService;
    }

    /**
     * The signed-in player's current and best streak, e.g.
     * {@code GET /api/players/me/streak?today=2026-11-17}. {@code today} is the player's local date,
     * which the server can't know: it decides whether the streak is still alive. 400 if it isn't
     * the date anywhere in the world.
     */
    @GetMapping("/api/players/me/streak")
    public Streak streak(@AuthenticationPrincipal Jwt token,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return streakService.forPlayer(playerService.forToken(token), today);
    }
}
