package dev.noahheller.pidoku.pidokuapi.profile;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleService;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleSource;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.googlePlayer;
import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class HistoryControllerTest extends IntegrationTest {

    /** When the first test solve finishes; later ones are some minutes after. */
    private static final Instant T0 = Instant.parse("2026-11-01T12:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    PuzzleService puzzleService;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    private long seed;

    private void name(String uid, String name) {
        assertThat(update(uid, "{\"displayName\": \"%s\"}".formatted(name))).hasStatusOk();
    }

    private void profileVisibility(String uid, String visibility) {
        assertThat(update(uid, "{\"profileVisibility\": \"%s\"}".formatted(visibility))).hasStatusOk();
    }

    private void historyVisibility(String uid, String visibility) {
        assertThat(update(uid, "{\"historyVisibility\": \"%s\"}".formatted(visibility))).hasStatusOk();
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

    private Puzzle pool() {
        return puzzleService.store(TestPuzzles.generated(Difficulty.EXPERT, ++seed), PuzzleSource.POOL);
    }

    private Puzzle daily() {
        return dailyPuzzleService.fillWithNew(new DailySlot(dailyPuzzleService.today(), Difficulty.HARD),
                TestPuzzles.generated(Difficulty.HARD, ++seed)).getPuzzle();
    }

    /** A solve taking {@code seconds}, finished {@code minutesLater} minutes after {@link #T0}. */
    private void solve(String uid, Puzzle puzzle, int seconds, int mistakes, int minutesLater) {
        Instant done = T0.plus(minutesLater, ChronoUnit.MINUTES);
        jdbc.update("""
                INSERT INTO attempts (player_id, puzzle_id, started_at, completed_at, elapsed_ms, mistakes)
                SELECT id, ?, ?::timestamptz - make_interval(secs => ?), ?::timestamptz, ?, ?
                FROM players WHERE firebase_uid = ?
                """, puzzle.getId(), done.toString(), seconds, done.toString(), seconds * 1000L, mistakes, uid);
    }

    private void unfinished(String uid, Puzzle puzzle) {
        jdbc.update("INSERT INTO attempts (player_id, puzzle_id, started_at) SELECT id, ?, now() FROM players WHERE firebase_uid = ?",
                puzzle.getId(), uid);
    }

    /** As {@code viewer} signed in with Google (with no Google name, so names set here stay). */
    private MvcTestResult history(String viewer, String of, String query) {
        return mvc.get().uri("/api/players/{id}/history" + query, idOf(of)).with(googlePlayer(viewer, null)).exchange();
    }

    private MvcTestResult history(String viewer, String of) {
        return history(viewer, of, "");
    }

    private MvcTestResult profile(String viewer, String of) {
        return mvc.get().uri("/api/players/{id}/profile?today={today}", idOf(of), dailyPuzzleService.today())
                .with(googlePlayer(viewer, null)).exchange();
    }

    @Test
    void listsSolvedPuzzlesNewestFirstRandomOnesIncludedButNotUnfinishedOnes() {
        name("alice", "Alice");
        Puzzle random = pool();
        Puzzle daily = daily();
        Puzzle started = pool();
        solve("alice", random, 300, 2, 0);
        solve("alice", daily, 200, 0, 10);
        // A replay of the random one, later and faster: the first solve is the one shown, where it was.
        solve("alice", random, 100, 0, 20);
        unfinished("alice", started);

        assertThat(history("alice", "alice")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.entries[*].puzzle.id").isEqualTo(List.of(daily.getId().intValue(), random.getId().intValue()));
            assertThat(json).extractingPath("$.entries[0].puzzle.dailyDate").isEqualTo(dailyPuzzleService.today().toString());
            assertThat(json).extractingPath("$.entries[1].puzzle.dailyDate").isNull();
            assertThat(json).extractingPath("$.entries[1].puzzle.shareCode").isEqualTo(random.getShareCode());
            assertThat(json).extractingPath("$.entries[1].solve.elapsedMs").isEqualTo(300000);
            // 5 s x 2 mistakes².
            assertThat(json).extractingPath("$.entries[1].solve.penaltyMs").isEqualTo(20000);
            assertThat(json).extractingPath("$.entries[1].solve.mistakes").isEqualTo(2);
            assertThat(json).extractingPath("$.entries[1].solve.completedAt").isEqualTo("2026-11-01T12:00:00Z");
            assertThat(json).extractingPath("$.entries[*].others").isEqualTo(List.of(List.of(), List.of()));
            assertThat(json).extractingPath("$.hasMore").isEqualTo(false);
        });
    }

    @Test
    void friendsSeeItByDefaultAndStrangersDont() {
        name("bob", "Bob");
        solve("bob", pool(), 100, 0, 0);
        befriend("alice", "bob");
        name("carol", "Carol");
        // Carol can see Bob's profile, but his history is still for friends.
        profileVisibility("bob", "public");

        assertThat(history("alice", "bob")).hasStatusOk().bodyJson().extractingPath("$.entries").asArray().hasSize(1);
        assertThat(profile("alice", "bob")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.historyVisibility").isEqualTo("friends");
            assertThat(json).extractingPath("$.historyVisible").isEqualTo(true);
        });
        assertThat(profile("carol", "bob")).hasStatusOk().bodyJson().extractingPath("$.historyVisible").isEqualTo(false);
        assertThat(history("carol", "bob")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aPublicHistoryIsSeenByAnyoneWhoCanSeeTheProfile() {
        name("bob", "Bob");
        solve("bob", pool(), 100, 0, 0);
        name("carol", "Carol");
        historyVisibility("bob", "public");

        // His profile is still for friends, and the history is on it.
        assertThat(history("carol", "bob")).hasStatus(HttpStatus.NOT_FOUND);

        profileVisibility("bob", "public");
        assertThat(history("carol", "bob")).hasStatusOk().bodyJson().extractingPath("$.entries").asArray().hasSize(1);
    }

    @Test
    void aPrivateHistoryIsHiddenFromFriendsButNotFromThemselves() {
        name("bob", "Bob");
        solve("bob", pool(), 100, 0, 0);
        befriend("alice", "bob");
        historyVisibility("bob", "private");

        assertThat(profile("alice", "bob")).hasStatusOk().bodyJson().extractingPath("$.historyVisible").isEqualTo(false);
        assertThat(history("alice", "bob")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(history("bob", "bob")).hasStatusOk().bodyJson().extractingPath("$.entries").asArray().hasSize(1);
    }

    @Test
    void eachPuzzleHasTheViewersAndTheirFriendsSolvesFastestFirst() {
        name("bob", "Bob");
        name("alice", "Alice");
        name("dave", "Dave");
        name("erin", "Erin");
        name("hidden", "Hidden");
        befriend("alice", "bob");
        befriend("alice", "dave");
        befriend("alice", "hidden");
        // Erin is Bob's friend, not Alice's: she isn't shown to Alice.
        befriend("bob", "erin");
        historyVisibility("hidden", "private");
        Puzzle shared = pool();
        solve("bob", shared, 300, 0, 0);
        solve("alice", shared, 250, 1, 1);
        solve("dave", shared, 200, 0, 2);
        solve("erin", shared, 100, 0, 3);
        solve("hidden", shared, 50, 0, 4);

        assertThat(history("alice", "bob")).hasStatusOk().bodyJson().satisfies(json -> {
            // Ranked like the leaderboards: Alice's 250 s plus 5 s for her mistake is behind Dave's 200 s.
            assertThat(json).extractingPath("$.entries[0].others[*].displayName").isEqualTo(List.of("Dave", "Alice"));
            assertThat(json).extractingPath("$.entries[0].others[*].you").isEqualTo(List.of(false, true));
            assertThat(json).extractingPath("$.entries[0].others[1].penaltyMs").isEqualTo(5000);
            assertThat(json).extractingPath("$.entries[0].others[0].playerId").isEqualTo((int) idOf("dave"));
        });
        // On Alice's own profile, her friends who solved it, Bob included.
        assertThat(history("alice", "alice")).hasStatusOk().bodyJson()
                .extractingPath("$.entries[0].others[*].displayName").isEqualTo(List.of("Dave", "Bob"));
    }

    @Test
    void inCommonKeepsOnlyPuzzlesTheViewerOrTheirFriendsSolvedToo() {
        name("bob", "Bob");
        befriend("alice", "bob");
        befriend("alice", "dave");
        Puzzle mine = pool();
        Puzzle davesToo = pool();
        Puzzle bobsAlone = pool();
        solve("bob", mine, 100, 0, 0);
        solve("bob", davesToo, 100, 0, 1);
        solve("bob", bobsAlone, 100, 0, 2);
        solve("alice", mine, 100, 0, 3);
        solve("dave", davesToo, 100, 0, 4);
        // Unfinished doesn't count as solved too.
        unfinished("alice", bobsAlone);

        assertThat(history("alice", "bob", "?inCommon=true")).hasStatusOk().bodyJson()
                .extractingPath("$.entries[*].puzzle.id").isEqualTo(List.of(davesToo.getId().intValue(), mine.getId().intValue()));
        // Bob has no other friends, so on his own profile only Alice's puzzle is in common.
        assertThat(history("bob", "bob", "?inCommon=true")).hasStatusOk().bodyJson()
                .extractingPath("$.entries[*].puzzle.id").isEqualTo(List.of(mine.getId().intValue()));
    }

    @Test
    void inCommonWithNoFriendsIsEmpty() {
        name("alice", "Alice");
        solve("alice", pool(), 100, 0, 0);

        assertThat(history("alice", "alice", "?inCommon=true")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.entries").asArray().isEmpty();
            assertThat(json).extractingPath("$.hasMore").isEqualTo(false);
        });
    }

    @Test
    void comesInPages() {
        name("alice", "Alice");
        for (int i = 0; i <= HistoryService.PAGE_SIZE; i++) {
            solve("alice", pool(), 100 + i, 0, i);
        }

        assertThat(history("alice", "alice")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.entries").asArray().hasSize(HistoryService.PAGE_SIZE);
            assertThat(json).extractingPath("$.entries[0].solve.elapsedMs").isEqualTo((100 + HistoryService.PAGE_SIZE) * 1000);
            assertThat(json).extractingPath("$.hasMore").isEqualTo(true);
        });
        assertThat(history("alice", "alice", "?offset=" + HistoryService.PAGE_SIZE)).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.entries[*].solve.elapsedMs").isEqualTo(List.of(100000));
            assertThat(json).extractingPath("$.hasMore").isEqualTo(false);
        });
    }

    @Test
    void anonymousPlayersCantSeeOtherPlayersHistories() {
        name("bob", "Bob");
        befriend("alice", "bob");

        assertThat(mvc.get().uri("/api/players/{id}/history", idOf("bob")).with(player("alice")))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/players/{id}/history", idOf("alice")).with(player("alice")))
                .hasStatusOk();
    }

    @Test
    void needsAToken() {
        assertThat(mvc.get().uri("/api/players/1/history")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
