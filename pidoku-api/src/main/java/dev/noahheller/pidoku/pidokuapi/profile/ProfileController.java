package dev.noahheller.pidoku.pidokuapi.profile;

import dev.noahheller.pidoku.pidokuapi.player.FirebaseTokens;
import dev.noahheller.pidoku.pidokuapi.player.PlayerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Player profiles. Needs a Firebase ID token (see SecurityConfig). */
@RestController
public class ProfileController {

    private final ProfileService profileService;
    private final HistoryService historyService;
    private final PlayerService playerService;

    public ProfileController(ProfileService profileService, HistoryService historyService, PlayerService playerService) {
        this.profileService = profileService;
        this.historyService = historyService;
        this.playerService = playerService;
    }

    /**
     * A player's profile, e.g. {@code GET /api/players/12/profile?today=2026-11-17}: streaks,
     * totals, best times and their last week of dailies. {@code today} is the viewer's local date.
     * 403 if it's someone else's and the viewer isn't signed in with Google; 404 if there's no such player or their {@link dev.noahheller.pidoku.pidokuapi.player.ProfileVisibility}
     * doesn't let the viewer see it; 400 if {@code today} isn't the date anywhere in the world.
     */
    @GetMapping("/api/players/{playerId}/profile")
    public PlayerProfile profile(@AuthenticationPrincipal Jwt token, @PathVariable long playerId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        return profileService.profile(playerService.forToken(token), FirebaseTokens.isGoogle(token), playerId, today);
    }

    /**
     * A page of a player's puzzle history, e.g. {@code GET /api/players/12/history?offset=20}: the
     * puzzles they've solved (dailies and random ones, never unfinished ones), newest first, each
     * with their first solve and the viewer's and the viewer's friends' solves of it.
     * {@code inCommon=true} keeps only puzzles the viewer or a friend of theirs has solved too.
     * 403 and 404 as for the profile, and 404 too if the profile is visible but the history isn't.
     */
    @GetMapping("/api/players/{playerId}/history")
    public PuzzleHistory history(@AuthenticationPrincipal Jwt token, @PathVariable long playerId,
                                 @RequestParam(defaultValue = "0") int offset,
                                 @RequestParam(defaultValue = "false") boolean inCommon) {
        return historyService.history(playerService.forToken(token), FirebaseTokens.isGoogle(token), playerId,
                offset, inCommon);
    }
}
