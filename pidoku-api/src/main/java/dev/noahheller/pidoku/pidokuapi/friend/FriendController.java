package dev.noahheller.pidoku.pidokuapi.friend;

import dev.noahheller.pidoku.pidokuapi.player.PlayerService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The signed-in player's friends. Every endpoint needs a Firebase ID token (see SecurityConfig). */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;
    private final PlayerService playerService;

    public FriendController(FriendService friendService, PlayerService playerService) {
        this.friendService = friendService;
        this.playerService = playerService;
    }

    @GetMapping
    public List<Friend> list(@AuthenticationPrincipal Jwt token) {
        return friendService.friendsOf(playerService.forToken(token));
    }

    /** @param friendCode the other player's code, from {@code GET /api/players/me} */
    public record AddRequest(String friendCode) {
    }

    /**
     * Adds the player with this friend code as a friend, both ways. 404 if no one has the code, 400
     * if it's the player's own.
     */
    @PostMapping
    public Friend add(@AuthenticationPrincipal Jwt token, @RequestBody AddRequest request) {
        return friendService.add(playerService.forToken(token), request.friendCode());
    }

    /** Removes a friend, for both players. */
    @DeleteMapping("/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt token, @PathVariable long friendId) {
        friendService.remove(playerService.forToken(token), friendId);
    }
}
