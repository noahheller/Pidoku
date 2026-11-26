package dev.noahheller.pidoku.pidokuapi.leaderboard;

import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.player.PlayerService;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Optional;

@RestController
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final PlayerService playerService;

    public LeaderboardController(LeaderboardService leaderboardService, PlayerService playerService) {
        this.leaderboardService = leaderboardService;
        this.playerService = playerService;
    }

    /**
     * The fastest solves of one daily, e.g.
     * {@code GET /api/puzzles/daily/2026-11-17/leaderboard?difficulty=hard&friends=true&clean=true}.
     * By default it ranks the fastest times whatever the mistakes; {@code clean=true} ranks only
     * solves with no mistakes. Sign-in is
     * optional, except with {@code friends=true}: with a token the viewer's own entry is marked and
     * included even beyond the top. 404 if the slot has no daily or its date hasn't begun anywhere.
     *
     * <p>Not cached: it changes with every solve (Spring Security's default headers say no-store).
     */
    @GetMapping("/api/puzzles/daily/{date}/leaderboard")
    public Leaderboard leaderboard(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam Difficulty difficulty,
                                   @RequestParam(defaultValue = "false") boolean friends,
                                   @RequestParam(defaultValue = "false") boolean clean,
                                   @AuthenticationPrincipal Jwt token) {
        Optional<Player> viewer = viewer(token, friends);
        return leaderboardService.forDaily(new DailySlot(date, difficulty), viewer, friends, clean);
    }

    /**
     * The fastest solves of any puzzle, by its share code, e.g.
     * {@code GET /api/puzzles/K7QX2M/leaderboard?friends=true&clean=true}: the board a challenge
     * link shows, for random, shared and (later) custom puzzles as well as dailies. Same switches
     * and sign-in rules as the daily leaderboard. 404 for an unknown code, or a daily whose date
     * hasn't begun anywhere.
     *
     * <p>Not cached: it changes with every solve.
     */
    @GetMapping("/api/puzzles/{shareCode}/leaderboard")
    public Leaderboard puzzleLeaderboard(@PathVariable String shareCode,
                                         @RequestParam(defaultValue = "false") boolean friends,
                                         @RequestParam(defaultValue = "false") boolean clean,
                                         @AuthenticationPrincipal Jwt token) {
        Optional<Player> viewer = viewer(token, friends);
        return leaderboardService.forPuzzle(shareCode, viewer, friends, clean);
    }

    /** The signed-in player, if any. A friends-only board needs one. */
    private Optional<Player> viewer(Jwt token, boolean friends) {
        if (friends && token == null) {
            throw new ErrorResponseException(HttpStatus.UNAUTHORIZED,
                    ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Sign in to see your friends' times."), null);
        }
        return playerService.find(token);
    }

    /**
     * For the home screen's daily cards: how many have solved each of a date's dailies and the
     * fastest time, e.g. {@code GET /api/puzzles/daily/2026-11-17/summary}. Sign-in is optional;
     * with a token it adds how many of the viewer's friends have solved each one and the fastest
     * among them and the viewer. 404 if the date hasn't begun anywhere.
     *
     * <p>Not cached, like the leaderboards: it changes with every solve.
     */
    @GetMapping("/api/puzzles/daily/{date}/summary")
    public DailySummary summary(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @AuthenticationPrincipal Jwt token) {
        return leaderboardService.summary(date, playerService.find(token));
    }
}
