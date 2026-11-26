package dev.noahheller.pidoku.pidokuapi.player;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.googlePlayer;
import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class PlayerMergeTest extends IntegrationTest {

    /**
     * Verifies the anonymous token in the request body: {@code anonymous.<uid>} or
     * {@code google.<uid>} stand for Firebase tokens of that kind; anything else doesn't verify.
     * Replaces the real decoder (Firebase's keys), which backs off when this bean exists. The
     * request's own token is set with {@code TestTokens} and never decoded.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class Tokens {
        @Bean
        JwtDecoder testJwtDecoder() {
            return token -> {
                String[] parts = token.split("\\.", 2);
                String provider = switch (parts[0]) {
                    case "anonymous" -> "anonymous";
                    case "google" -> "google.com";
                    default -> throw new BadJwtException("Not a test token");
                };
                Instant now = Instant.now();
                return Jwt.withTokenValue(token).header("alg", "none").subject(parts[1])
                        .claim("firebase", Map.of("sign_in_provider", provider))
                        .issuedAt(now).expiresAt(now.plus(1, ChronoUnit.HOURS)).build();
            };
        }
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    LocalDate today;
    Puzzle puzzle;

    @BeforeEach
    void storePuzzle() {
        today = dailyPuzzleService.today();
        puzzle = dailyPuzzleService.fillWithNew(new DailySlot(today, Difficulty.EASY), TestPuzzles.generated(Difficulty.EASY)).getPuzzle();
    }

    private long addPlayer(String uid, String name) {
        jdbc.update("INSERT INTO players (firebase_uid, display_name) VALUES (?, ?) ON CONFLICT DO NOTHING", uid, name);
        return id(uid);
    }

    private long id(String uid) {
        return jdbc.queryForObject("SELECT id FROM players WHERE firebase_uid = ?", Long.class, uid);
    }

    private void solve(String uid, int seconds) {
        jdbc.update("""
                INSERT INTO attempts (player_id, puzzle_id, started_at, completed_at, elapsed_ms)
                SELECT id, ?, now() - interval '10 minutes', now() - interval '10 minutes' + make_interval(secs => ?), ?
                FROM players WHERE firebase_uid = ?
                """, puzzle.getId(), seconds, seconds * 1000L, uid);
    }

    private void start(String uid) {
        jdbc.update("INSERT INTO attempts (player_id, puzzle_id, started_at) SELECT id, ?, now() FROM players WHERE firebase_uid = ?",
                puzzle.getId(), uid);
    }

    private void befriend(String a, String b) {
        jdbc.update("""
                INSERT INTO friendships (player_id, friend_id)
                SELECT x.id, y.id FROM players x, players y
                WHERE (x.firebase_uid, y.firebase_uid) IN ((?, ?), (?, ?))
                """, a, b, b, a);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private MvcTestResult merge(RequestPostProcessor as, String anonymousToken) {
        return mvc.post().uri("/api/players/me/merge").with(as)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anonymousToken\": \"" + anonymousToken + "\"}")
                .exchange();
    }

    @Test
    void movesTheAnonymousSolveOntoTheGooglePlayersLeaderboardEntry() {
        addPlayer("google-uid", "Noah");
        addPlayer("anon-uid", "Anonymous Pi");
        solve("anon-uid", 200);

        assertThat(merge(googlePlayer("google-uid", "Noah"), "anonymous.anon-uid")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(count("SELECT count(*) FROM players WHERE firebase_uid = 'anon-uid'")).isZero();
        assertThat(mvc.get().uri("/api/puzzles/daily/{date}/leaderboard?difficulty=easy", today))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"total": 1, "entries": [{"rank": 1, "displayName": "Noah", "elapsedMs": 200000}]}
                        """);
    }

    @Test
    void ranksTheEarlierOfTwoSolvesOfTheSameDaily() {
        addPlayer("google-uid", "Noah");
        addPlayer("anon-uid", null);
        solve("anon-uid", 100); // Finished first, so it's the one that ranks.
        solve("google-uid", 300);

        merge(googlePlayer("google-uid", "Noah"), "anonymous.anon-uid");

        assertThat(mvc.get().uri("/api/puzzles/daily/{date}/leaderboard?difficulty=easy", today))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"total": 1, "entries": [{"displayName": "Noah", "elapsedMs": 100000}]}
                        """);
        assertThat(count("SELECT count(*) FROM attempts WHERE player_id = ?", id("google-uid"))).isEqualTo(2);
    }

    @Test
    void keepsTheGooglePlayersUnfinishedAttemptWhenBothHaveOne() {
        long google = addPlayer("google-uid", "Noah");
        addPlayer("anon-uid", null);
        start("google-uid");
        start("anon-uid");

        assertThat(merge(googlePlayer("google-uid", "Noah"), "anonymous.anon-uid")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(count("SELECT count(*) FROM attempts")).isOne();
        assertThat(count("SELECT count(*) FROM attempts WHERE player_id = ?", google)).isOne();
    }

    @Test
    void movesFriendshipsBothWaysWithoutDuplicatesOrSelfFriendship() {
        addPlayer("google-uid", "Noah");
        addPlayer("anon-uid", null);
        addPlayer("bob", "Bob");
        addPlayer("carol", "Carol");
        befriend("anon-uid", "bob");
        befriend("anon-uid", "carol");
        befriend("google-uid", "carol");
        befriend("anon-uid", "google-uid");

        merge(googlePlayer("google-uid", "Noah"), "anonymous.anon-uid");

        long google = id("google-uid");
        assertThat(count("SELECT count(*) FROM friendships WHERE player_id = ?", google)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM friendships WHERE friend_id = ?", google)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM friendships")).isEqualTo(4);
    }

    @Test
    void movesEventsPaymentsAndTheSupporterBadge() {
        addPlayer("google-uid", "Noah");
        long anon = addPlayer("anon-uid", null);
        jdbc.update("UPDATE players SET supporter_since = now() - interval '1 day' WHERE id = ?", anon);
        jdbc.update("INSERT INTO events (player_id, type) VALUES (?, 'share')", anon);
        jdbc.update("INSERT INTO kofi_payments (transaction_id, player_id) VALUES ('tx-1', ?)", anon);
        jdbc.update("INSERT INTO supporter_codes (player_id, code, expires_at) VALUES (?, 'PI-ANON', now() + interval '1 day')", anon);

        merge(googlePlayer("google-uid", "Noah"), "anonymous.anon-uid");

        long google = id("google-uid");
        assertThat(count("SELECT count(*) FROM events WHERE player_id = ?", google)).isOne();
        assertThat(count("SELECT count(*) FROM kofi_payments WHERE player_id = ?", google)).isOne();
        assertThat(count("SELECT count(*) FROM supporter_codes WHERE player_id = ? AND code = 'PI-ANON'", google)).isOne();
        assertThat(count("SELECT count(*) FROM players WHERE id = ? AND supporter_since IS NOT NULL AND supporter_until IS NULL", google)).isOne();
    }

    @Test
    void takesTheAnonymousNameOnlyWhenTheGooglePlayerHasNone() {
        addPlayer("google-uid", null);
        addPlayer("anon-uid", "Pi Fan");

        merge(googlePlayer("google-uid", null), "anonymous.anon-uid");

        assertThat(jdbc.queryForObject("SELECT display_name FROM players WHERE firebase_uid = 'google-uid'", String.class)).isEqualTo("Pi Fan");
    }

    @Test
    void createsTheGooglePlayerIfItHasNotMadeARequestYet() {
        addPlayer("anon-uid", null);
        solve("anon-uid", 200);

        assertThat(merge(googlePlayer("google-uid", "Noah"), "anonymous.anon-uid")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(count("SELECT count(*) FROM attempts WHERE player_id = ?", id("google-uid"))).isOne();
    }

    @Test
    void doesNothingWhenThereIsNoAnonymousPlayer() {
        addPlayer("google-uid", "Noah");
        solve("google-uid", 200);

        assertThat(merge(googlePlayer("google-uid", "Noah"), "anonymous.never-played")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(merge(googlePlayer("google-uid", "Noah"), "anonymous.never-played")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(count("SELECT count(*) FROM attempts")).isOne();
    }

    @Test
    void refusesATokenThatDoesNotVerify() {
        addPlayer("anon-uid", null);

        assertThat(merge(googlePlayer("google-uid", "Noah"), "forged.anon-uid")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(count("SELECT count(*) FROM players WHERE firebase_uid = 'anon-uid'")).isOne();
    }

    @Test
    void refusesToMoveAGooglePlayer() {
        addPlayer("other-google", "Other");
        solve("other-google", 200);

        assertThat(merge(googlePlayer("google-uid", "Noah"), "google.other-google")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(count("SELECT count(*) FROM players WHERE firebase_uid = 'other-google'")).isOne();
    }

    @Test
    void refusesWhenNotSignedInWithGoogle() {
        addPlayer("anon-uid", null);

        assertThat(merge(player("someone-else"), "anonymous.anon-uid")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(count("SELECT count(*) FROM players WHERE firebase_uid = 'anon-uid'")).isOne();
    }

    @Test
    void needsSignIn() {
        assertThat(mvc.post().uri("/api/players/me/merge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"anonymousToken\": \"anonymous.anon-uid\"}")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
