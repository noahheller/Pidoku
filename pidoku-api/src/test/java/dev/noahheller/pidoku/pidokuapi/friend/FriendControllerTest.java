package dev.noahheller.pidoku.pidokuapi.friend;

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

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class FriendControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    /** The player's friend code, assigned by {@code GET /api/players/me}. */
    private String codeOf(String uid) {
        assertThat(mvc.get().uri("/api/players/me").with(player(uid))).hasStatusOk();
        return jdbc.queryForObject("SELECT friend_code FROM players WHERE firebase_uid = ?", String.class, uid);
    }

    private void name(String uid, String name) {
        mvc.patch().uri("/api/players/me").with(player(uid))
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\": \"%s\"}".formatted(name)).exchange();
    }

    private MvcTestResult add(String uid, String code) {
        return mvc.post().uri("/api/friends").with(player(uid))
                .contentType(MediaType.APPLICATION_JSON).content("{\"friendCode\": \"%s\"}".formatted(code)).exchange();
    }

    private MvcTestResult friends(String uid) {
        return mvc.get().uri("/api/friends").with(player(uid)).exchange();
    }

    private long idOf(String uid) {
        return jdbc.queryForObject("SELECT id FROM players WHERE firebase_uid = ?", Long.class, uid);
    }

    @Test
    void addingByCodeMakesBothPlayersFriends() {
        name("bob", "Bob");
        name("alice", "Alice");

        assertThat(add("alice", codeOf("bob"))).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"id": %d, "displayName": "Bob", "currentStreak": null}
                """.formatted(idOf("bob")));

        assertThat(friends("alice")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                [{"id": %d, "displayName": "Bob", "currentStreak": null}]
                """.formatted(idOf("bob")));
        assertThat(friends("bob")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                [{"id": %d, "displayName": "Alice", "currentStreak": null}]
                """.formatted(idOf("alice")));
    }

    @Test
    void codesIgnoreCaseAndSurroundingSpaces() {
        assertThat(add("alice", "  " + codeOf("bob").toLowerCase() + " ")).hasStatusOk();
    }

    @Test
    void addingAFriendTwiceChangesNothing() {
        String bob = codeOf("bob");
        add("alice", bob);

        assertThat(add("alice", bob)).hasStatusOk();
        assertThat(add("bob", codeOf("alice"))).hasStatusOk();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM friendships", Integer.class)).isEqualTo(2);
    }

    @Test
    void friendsAreListedByName() {
        name("bob", "bob");
        name("carol", "Carol");
        name("dave", "Ann");
        codeOf("nameless");
        for (String friend : new String[]{"carol", "nameless", "bob", "dave"}) {
            add("alice", codeOf(friend));
        }

        assertThat(friends("alice")).bodyJson().extractingPath("$[*].displayName")
                .isEqualTo(java.util.Arrays.asList("Ann", "bob", "Carol", null));
    }

    @Test
    void removingAFriendRemovesItForBoth() {
        add("alice", codeOf("bob"));

        assertThat(mvc.delete().uri("/api/friends/{id}", idOf("bob")).with(player("alice")))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(friends("alice")).bodyJson().isStrictlyEqualTo("[]");
        assertThat(friends("bob")).bodyJson().isStrictlyEqualTo("[]");
    }

    @Test
    void removingSomeoneWhoIsntAFriendChangesNothing() {
        add("alice", codeOf("bob"));
        codeOf("carol");

        assertThat(mvc.delete().uri("/api/friends/{id}", idOf("carol")).with(player("bob")))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM friendships", Integer.class)).isEqualTo(2);
    }

    @Test
    void withTheDateEachFriendComesWithTheirStreak() {
        LocalDate today = dailyPuzzleService.today();
        add("alice", codeOf("bob"));
        add("alice", codeOf("carol"));
        solveDaily("bob", today.minusDays(1), 1);
        solveDaily("bob", today, 2);

        assertThat(mvc.get().uri("/api/friends?today={today}", today).with(player("alice")))
                .hasStatusOk()
                .bodyJson().extractingPath("$[*].currentStreak").isEqualTo(List.of(2, 0));
    }

    @Test
    void aStreakDateThatIsntTodayAnywhereIsABadRequest() {
        add("alice", codeOf("bob"));

        assertThat(mvc.get().uri("/api/friends?today={today}", dailyPuzzleService.today().plusDays(3)).with(player("alice")))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    /** Solves the date's easy daily at noon UTC on that date. */
    private void solveDaily(String uid, LocalDate date, long seed) {
        Puzzle puzzle = dailyPuzzleService.fillWithNew(new DailySlot(date, Difficulty.EASY),
                TestPuzzles.generated(Difficulty.EASY, seed)).getPuzzle();
        jdbc.update("""
                INSERT INTO attempts (player_id, puzzle_id, started_at, completed_at, elapsed_ms)
                SELECT id, ?, ?::timestamptz - interval '5 minutes', ?::timestamptz, 300000
                FROM players WHERE firebase_uid = ?
                """, puzzle.getId(), date + "T12:00:00Z", date + "T12:00:00Z", uid);
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
        add("alice", codeOf("bob"));
        solveAt("bob", today.minusDays(20), Difficulty.HARD, 200, 0, 1);
        solveAt("bob", today.minusDays(1), Difficulty.EASY, 90, 2, 2);
        solveAt("bob", today, Difficulty.HARD, 300, 0, 3);
        solveAt("bob", today, Difficulty.EASY, 60, 0, 4);

        assertThat(mvc.get().uri("/api/friends/{id}?today={today}", idOf("bob"), today).with(player("alice")))
                .hasStatusOk().bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.displayName").isEqualTo("Bob");
                    assertThat(json).extractingPath("$.currentStreak").isEqualTo(2);
                    assertThat(json).extractingPath("$.bestStreak").isEqualTo(2);
                    assertThat(json).extractingPath("$.dailiesSolved").isEqualTo(4);
                    assertThat(json).extractingPath("$.cleanSolves").isEqualTo(3);
                    assertThat(json).extractingPath("$.bestTimes").isEqualTo(java.util.Map.of("easy", 60000, "hard", 200000));
                    // The solve 20 days ago isn't recent; newest date first, easiest first within it.
                    assertThat(json).extractingPath("$.recent[*].elapsedMs").isEqualTo(List.of(60000, 300000, 90000));
                    assertThat(json).extractingPath("$.recent[0].difficulty").isEqualTo("easy");
                });
    }

    @Test
    void aPlayerWhoIsntAFriendHasNoProfile() {
        codeOf("bob");

        assertThat(mvc.get().uri("/api/friends/{id}?today={today}", idOf("bob"), dailyPuzzleService.today()).with(player("alice")))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void anUnknownCodeIsNotFound() {
        assertThat(add("alice", "ZZZZZZZZ"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").asString().contains("ZZZZZZZZ");
    }

    @Test
    void yourOwnCodeIsABadRequest() {
        assertThat(add("alice", codeOf("alice")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("your own");
    }

    @Test
    void needsAToken() {
        assertThat(mvc.get().uri("/api/friends")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/friends").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
