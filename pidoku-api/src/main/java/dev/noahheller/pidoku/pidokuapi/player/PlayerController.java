package dev.noahheller.pidoku.pidokuapi.player;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
public class PlayerController {

    private final PlayerService playerService;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;

    public PlayerController(PlayerService playerService, JwtDecoder jwtDecoder, Clock clock) {
        this.playerService = playerService;
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;
    }

    /**
     * The player the request's Firebase ID token belongs to, with the friend code friends add them
     * by (assigned on the first call).
     */
    @GetMapping("/api/players/me")
    public PlayerResponse me(@AuthenticationPrincipal Jwt token) {
        return PlayerResponse.from(playerService.me(token), clock.instant());
    }

    /**
     * Any of them may be given; a field left out (or null) stays as it is. A request with none is
     * refused like an empty name.
     *
     * @param displayName       checked by {@link DisplayName}
     * @param profileVisibility a {@link ProfileVisibility} id: "public", "friends" or "private"
     * @param historyVisibility who can see the puzzle history on the profile, as a
     *                          {@link ProfileVisibility} id
     */
    public record UpdateRequest(String displayName, String profileVisibility, String historyVisibility) {
    }

    /**
     * Changes the signed-in player's details: the display name friends see (trimmed, 1–40
     * characters), who can see their profile, and who can see their puzzle history. 400 if any
     * isn't allowed, and then none changes.
     */
    @PatchMapping("/api/players/me")
    public PlayerResponse update(@AuthenticationPrincipal Jwt token, @RequestBody UpdateRequest request) {
        return PlayerResponse.from(playerService.update(token, request.displayName(), request.profileVisibility(),
                request.historyVisibility()), clock.instant());
    }

    /** @param anonymousToken the Firebase ID token of the anonymous account to move in */
    public record MergeRequest(String anonymousToken) {
    }

    /**
     * Moves an anonymous player into the signed-in Google player (see
     * {@link PlayerService#mergeAnonymous}): for a browser that played anonymously and then signed
     * in with a Google account that already had a player. The anonymous token is verified like the
     * request's own. 204, also when there was no anonymous player; 400 if the token doesn't verify
     * or isn't anonymous, or the request isn't signed in with Google.
     */
    @PostMapping("/api/players/me/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void merge(@AuthenticationPrincipal Jwt token, @RequestBody MergeRequest request) {
        if (request.anonymousToken() == null || request.anonymousToken().isBlank()) {
            throw new InvalidMergeException("anonymousToken is required.");
        }
        Jwt anonymous;
        try {
            anonymous = jwtDecoder.decode(request.anonymousToken());
        } catch (JwtException e) {
            throw new InvalidMergeException("The anonymous sign-in couldn't be verified.");
        }
        playerService.mergeAnonymous(token, anonymous);
    }

    /**
     * Deletes the signed-in player and everything kept about them: attempts, analytics events,
     * friendships, supporter code and Ko-fi payment records, and the player row. 204 even if there was no player, so a retried delete succeeds.
     */
    @DeleteMapping("/api/players/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt token) {
        playerService.delete(token);
    }
}
