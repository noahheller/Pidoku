package dev.noahheller.pidoku.pidokuapi.supporter;

import dev.noahheller.pidoku.pidokuapi.player.PlayerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SupporterController {

    private final SupporterService supporterService;
    private final PlayerService playerService;

    public SupporterController(SupporterService supporterService, PlayerService playerService) {
        this.supporterService = supporterService;
        this.playerService = playerService;
    }

    /**
     * A new single-use code for the signed-in player to paste into their Ko-fi message, replacing
     * any code they had; it works for {@code SupporterService.CODE_LIFETIME}. The tip it comes with
     * makes them a supporter.
     */
    @PostMapping("/api/players/me/supporter-code")
    public SupporterService.SupporterCode newCode(@AuthenticationPrincipal Jwt token) {
        return supporterService.newCode(playerService.forToken(token));
    }
}
