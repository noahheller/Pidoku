package dev.noahheller.pidoku.pidokuapi.stats;

import dev.noahheller.pidoku.pidokuapi.player.PlayerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class StatsController {

    private final StatsService statsService;
    private final PlayerService playerService;

    public StatsController(StatsService statsService, PlayerService playerService) {
        this.statsService = statsService;
        this.playerService = playerService;
    }

    /**
     * The signed-in player's stats per difficulty, e.g.
     * {@code GET /api/players/me/stats?today=2026-11-26&source=daily&assisted=true}.
     * {@code source} is all (the default), daily or random. Solves with hints or auto notes are
     * left out unless {@code assisted=true}, so averages compare like with like. {@code today} is
     * the player's local date, for the streak. 400 for an unknown source, or a {@code today} that
     * isn't the date anywhere.
     */
    @GetMapping("/api/players/me/stats")
    public PlayerStats stats(@AuthenticationPrincipal Jwt token,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today,
                             @RequestParam(defaultValue = "all") String source,
                             @RequestParam(defaultValue = "false") boolean assisted) {
        return statsService.forPlayer(playerService.forToken(token), StatsSource.fromId(source), assisted, today);
    }
}
