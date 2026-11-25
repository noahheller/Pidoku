package dev.noahheller.pidoku.pidokuapi.friend;

import dev.noahheller.pidoku.pidokuapi.player.FirebaseTokens;
import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.player.PlayerService;
import dev.noahheller.pidoku.pidokuapi.profile.PlayerProfile;
import dev.noahheller.pidoku.pidokuapi.profile.ProfileService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** The signed-in player's friends. Every endpoint needs a Firebase ID token (see SecurityConfig). */
@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;
    private final PlayerService playerService;
    private final ProfileService profileService;

    public FriendController(FriendService friendService, PlayerService playerService, ProfileService profileService) {
        this.friendService = friendService;
        this.playerService = playerService;
        this.profileService = profileService;
    }

    /**
     * The player's friends. With {@code ?today=2026-11-17} (the player's local date) each comes with
     * their current daily streak; 400 if that isn't the date anywhere in the world.
     */
    @GetMapping
    public List<Friend> list(@AuthenticationPrincipal Jwt token,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return friendService.friendsOf(playerService.forToken(token), today);
    }

    /**
     * One friend's profile, e.g. {@code GET /api/friends/12?today=2026-11-17}. Kept for app
     * versions from before {@code GET /api/players/{id}/profile}, which replaces it. 404 if they
     * aren't friends, or their profile is private.
     */
    @GetMapping("/{friendId}")
    public PlayerProfile profile(@AuthenticationPrincipal Jwt token, @PathVariable long friendId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        Player viewer = playerService.forToken(token);
        PlayerProfile profile = profileService.profile(viewer, FirebaseTokens.isGoogle(token), friendId, today);
        if (profile.friendsSince() == null) {
            throw new FriendNotFoundException(friendId);
        }
        return profile;
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
