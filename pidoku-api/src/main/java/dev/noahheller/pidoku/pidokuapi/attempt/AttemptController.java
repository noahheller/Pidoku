package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.player.PlayerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** A player's attempts at puzzles. Every endpoint needs a Firebase ID token (see SecurityConfig). */
@RestController
@RequestMapping("/api/attempts")
public class AttemptController {

    private final AttemptService attemptService;
    private final PlayerService playerService;

    public AttemptController(AttemptService attemptService, PlayerService playerService) {
        this.attemptService = attemptService;
        this.playerService = playerService;
    }

    /** @param restart true when the player restarted the puzzle; may be left out */
    public record StartRequest(@NotNull Long puzzleId, Boolean restart) {

        boolean isRestart() {
            return Boolean.TRUE.equals(restart);
        }
    }

    /**
     * @param grid     the finished grid, flat and row-major like the puzzle's {@code solution}
     * @param mistakes wrong digits entered along the way
     * @param activeMs the client's own timer, paused while the tab was hidden; may be left out
     * @param lateByMs how long ago the puzzle was solved, when the completion couldn't be sent at
     *                 the time; left out when it's sent straight away
     */
    public record CompleteRequest(@NotNull @Size(max = 625) List<@NotNull Integer> grid, @Min(0) int mistakes,
                                  @Min(0) Long activeMs, @Min(0) Long lateByMs) {
    }

    /**
     * Starts the clock on a puzzle: the frontend calls this when the player opens it (for a daily,
     * when they press Start). Returns the
     * player's unfinished attempt at the puzzle, creating it if there isn't one, so opening the
     * puzzle again doesn't restart the clock. With {@code "restart": true} (the player wiped the
     * board and started over) the unfinished attempt's clock starts again from now.
     */
    @PostMapping
    public AttemptResponse start(@AuthenticationPrincipal Jwt token, @Valid @RequestBody StartRequest request) {
        return AttemptResponse.from(attemptService.start(playerService.forToken(token), request.puzzleId(), request.isRestart()));
    }

    /**
     * Completes an attempt with the solved grid; 422 if the grid isn't the solution. The response's
     * {@code elapsedMs} is the solve time as the server measured it, less {@code lateByMs} for a
     * completion sent late.
     */
    @PostMapping("/{id}/complete")
    public AttemptResponse complete(@AuthenticationPrincipal Jwt token, @PathVariable long id,
                                    @Valid @RequestBody CompleteRequest request) {
        int[] grid = request.grid().stream().mapToInt(Integer::intValue).toArray();
        return AttemptResponse.from(
                attemptService.complete(playerService.forToken(token), id, grid, request.mistakes(),
                        request.activeMs(), request.lateByMs()));
    }
}
