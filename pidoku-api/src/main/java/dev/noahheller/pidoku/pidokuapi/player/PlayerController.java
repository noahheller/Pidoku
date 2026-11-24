package dev.noahheller.pidoku.pidokuapi.player;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    /**
     * Either or both may be given; a field left out (or null) stays as it is. A request with neither
     * is refused like an empty name.
     *
     * @param displayName       checked by {@link DisplayName}
     * @param profileVisibility a {@link ProfileVisibility} id: "public", "friends" or "private"
     */
    public record UpdateRequest(String displayName, String profileVisibility) {
    }

    /**
     * Changes the signed-in player's details: the display name friends see (trimmed, 1–40
     * characters) and who can see their profile. 400 if either isn't allowed, and then neither
     * changes.
     */
    @PatchMapping("/api/players/me")
    public PlayerResponse update(@AuthenticationPrincipal Jwt token, @RequestBody UpdateRequest request) {
        return PlayerResponse.from(playerService.update(token, request.displayName(), request.profileVisibility()));
    }

    /**
     * Deletes the signed-in player and everything kept about them: attempts, analytics events,
     * friendships and the player row. 204 even if there was no player, so a retried delete succeeds.
     */
    @DeleteMapping("/api/players/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt token) {
        playerService.delete(token);
    }
}
