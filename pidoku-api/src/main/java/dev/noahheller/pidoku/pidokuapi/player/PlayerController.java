package dev.noahheller.pidoku.pidokuapi.player;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    /** The player the request's Firebase ID token belongs to. */
    @GetMapping("/api/players/me")
    public PlayerResponse me(@AuthenticationPrincipal Jwt token) {
        return PlayerResponse.from(playerService.forToken(token));
    }
}
