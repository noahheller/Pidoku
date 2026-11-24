package dev.noahheller.pidoku.pidokuapi.profile;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.googlePlayer;
import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class ProfileControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    private void name(String uid, String name) {
        assertThat(update(uid, "{\"displayName\": \"%s\"}".formatted(name))).hasStatusOk();
    }

    private void visibility(String uid, String visibility) {
        assertThat(update(uid, "{\"profileVisibility\": \"%s\"}".formatted(visibility))).hasStatusOk();
    }

    private MvcTestResult update(String uid, String json) {
        return mvc.patch().uri("/api/players/me").with(player(uid))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private void befriend(String a, String b) {
        assertThat(mvc.get().uri("/api/players/me").with(player(b))).hasStatusOk();
        String code = jdbc.queryForObject("SELECT friend_code FROM players WHERE firebase_uid = ?", String.class, b);
        assertThat(mvc.post().uri("/api/friends").with(player(a))
                .contentType(MediaType.APPLICATION_JSON).content("{\"friendCode\": \"%s\"}".formatted(code))).hasStatusOk();
    }

    private long idOf(String uid) {
        return jdbc.queryForObject("SELECT id FROM players WHERE firebase_uid = ?", Long.class, uid);
    }

    /** As {@code viewer} signed in with Google (with no Google name, so names set here stay). */
    private MvcTestResult profile(String viewer, String of) {
        return mvc.get().uri("/api/players/{id}/profile?today={today}", idOf(of), dailyPuzzleService.today())
                .with(googlePlayer(viewer, null)).exchange();
    }

    private void solveAt(String uid, LocalDate date, Difficulty difficulty, int seconds, int mistakes, long seed) {
        Puzzle puzzle = dailyPuzzleService.fillWithNew(new DailySlot(date, difficulty),
                TestPuzzles.generated(difficulty, seed)).getPuzzle();
        jdbc.update("""
                INSERT INTO attempts (player_id, puzzle_id, started_at, completed_at, elapsed_ms, mistakes)
                SELECT id, ?, ?::timestamptz - make_interval(secs => ?), ?::timestamptz, ?, ?
                FROM players WHERE firebase_uid = ?
                """, puzzle.getId(), date + "T12:00:00Z", seconds, date + "T12:00:00Z", seconds * 1000L, mistakes, uid);
    }

    @Test
    void aFriendsProfileHasTheirStreaksTotalsBestTimesAndRecentDailies() {
        LocalDate today = dailyPuzzleService.today();
        name("bob", "Bob");
        befriend("alice", "bob");
        solveAt("bob", today.minusDays(20), Difficulty.HARD, 200, 0, 1);
        solveAt("bob", today.minusDays(1), Difficulty.EASY, 90, 2, 2);
        solveAt("bob", today, Difficulty.HARD, 300, 0, 3);
        solveAt("bob", today, Difficulty.EASY, 60, 0, 4);

        assertThat(profile("alice", "bob")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.displayName").isEqualTo("Bob");
            assertThat(json).extractingPath("$.you").isEqualTo(false);
            assertThat(json).extractingPath("$.visibility").isEqualTo("friends");
            assertThat(json).extractingPath("$.friendsSince").isNotNull();
            assertThat(json).extractingPath("$.currentStreak").isEqualTo(2);
            assertThat(json).extractingPath("$.bestStreak").isEqualTo(2);
            assertThat(json).extractingPath("$.dailiesSolved").isEqualTo(4);
            assertThat(json).extractingPath("$.cleanSolves").isEqualTo(3);
            assertThat(json).extractingPath("$.bestTimes").isEqualTo(Map.of("easy", 60000, "hard", 200000));
            // The solve 20 days ago isn't recent; newest date first, easiest first within it.
            assertThat(json).extractingPath("$.recent[*].elapsedMs").isEqualTo(List.of(60000, 300000, 90000));
            assertThat(json).extractingPath("$.recent[0].difficulty").isEqualTo("easy");
        });
    }

    @Test
    void everyoneCanSeeTheirOwnProfileEvenWhenPrivate() {
        name("alice", "Alice");
        visibility("alice", "private");

        assertThat(profile("alice", "alice")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.you").isEqualTo(true);
            assertThat(json).extractingPath("$.visibility").isEqualTo("private");
            assertThat(json).extractingPath("$.friendsSince").isNull();
        });
    }

    @Test
    void aFriendsOnlyProfileIsHiddenFromStrangers() {
        name("bob", "Bob");

        assertThat(profile("alice", "bob")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aPublicProfileIsSeenByAnyone() {
        name("bob", "Bob");
        visibility("bob", "public");

        assertThat(profile("alice", "bob")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.displayName").isEqualTo("Bob");
            assertThat(json).extractingPath("$.friendsSince").isNull();
        });
    }

    @Test
    void aPrivateProfileIsHiddenEvenFromFriends() {
        name("bob", "Bob");
        befriend("alice", "bob");
        visibility("bob", "private");

        assertThat(profile("alice", "bob")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aMissingPlayerLooksLikeAHiddenOne() {
        name("alice", "Alice");

        assertThat(mvc.get().uri("/api/players/{id}/profile?today={today}", 999_999, dailyPuzzleService.today())
                .with(googlePlayer("alice", null))).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void needsAToken() {
        assertThat(mvc.get().uri("/api/players/1/profile?today={today}", dailyPuzzleService.today()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anonymousPlayersCantSeeOtherPlayersProfiles() {
        name("bob", "Bob");
        visibility("bob", "public");
        name("alice", "Alice");

        assertThat(mvc.get().uri("/api/players/{id}/profile?today={today}", idOf("bob"), dailyPuzzleService.today())
                .with(player("alice")))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.detail").asString().contains("Sign in with Google");
    }

    @Test
    void anonymousPlayersCanSeeTheirOwnProfile() {
        name("alice", "Alice");

        assertThat(mvc.get().uri("/api/players/{id}/profile?today={today}", idOf("alice"), dailyPuzzleService.today())
                .with(player("alice"))).hasStatusOk();
    }
}
