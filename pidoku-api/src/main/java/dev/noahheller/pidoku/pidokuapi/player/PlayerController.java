package dev.noahheller.pidoku.pidokuapi.player;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    /**
     * The player the request's Firebase ID token belongs to, with the friend code friends add them
     * by (assigned on the first call).
     */
    @GetMapping("/api/players/me")
    public PlayerResponse me(@AuthenticationPrincipal Jwt token) {
        return PlayerResponse.from(playerService.me(token));
    }

    /** @param displayName checked by {@link DisplayName}; null is refused like an empty name */
    public record UpdateRequest(String displayName) {
    }

    /**
     * Changes the signed-in player's details. Today that's only the display name friends see
     * (trimmed, 1–40 characters); 400 if it isn't allowed.
     */
    @PatchMapping("/api/players/me")
    public PlayerResponse update(@AuthenticationPrincipal Jwt token, @RequestBody UpdateRequest request) {
        return PlayerResponse.from(playerService.rename(token, request.displayName()));
    }
}
