package dev.noahheller.pidoku.pidokuapi.leaderboard;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.LocalDate;
import java.util.List;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class LeaderboardControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    LocalDate today;
    Puzzle hard;

    @BeforeEach
    void storeDaily() {
        today = dailyPuzzleService.today();
        hard = dailyPuzzleService.fillWithNew(new DailySlot(today, Difficulty.HARD), TestPuzzles.generated(Difficulty.HARD)).getPuzzle();
    }

    private void addPlayer(String uid, String name) {
        jdbc.update("INSERT INTO players (firebase_uid, display_name) VALUES (?, ?) ON CONFLICT DO NOTHING", uid, name);
    }

    /**
     * Records a finished attempt directly, solved {@code minutesAgo} before now and taking
     * {@code seconds}.
     */
    private void solve(String uid, Puzzle puzzle, int seconds, int minutesAgo, boolean late) {
        solve(uid, puzzle, seconds, minutesAgo, late, 1);
    }

    private void solve(String uid, Puzzle puzzle, int seconds, int minutesAgo, boolean late, int mistakes) {
        addPlayer(uid, null);
        jdbc.update("""
                INSERT INTO attempts (player_id, puzzle_id, started_at, completed_at, elapsed_ms, mistakes, late)
                SELECT id, ?, now() - make_interval(mins => ?, secs => ?), now() - make_interval(mins => ?), ?, ?, ?
                FROM players WHERE firebase_uid = ?
                """, puzzle.getId(), minutesAgo, seconds, minutesAgo, seconds * 1000L, mistakes, late, uid);
    }

    private void solve(String uid, int seconds) {
        solve(uid, hard, seconds, 10, false);
    }

    private void befriend(String a, String b) {
        jdbc.update("""
                INSERT INTO friendships (player_id, friend_id)
                SELECT x.id, y.id FROM players x, players y
                WHERE (x.firebase_uid, y.firebase_uid) IN ((?, ?), (?, ?))
                """, a, b, b, a);
    }

    private MvcTestResult leaderboard(String query) {
        return mvc.get().uri("/api/puzzles/daily/{date}/leaderboard?" + query, today).exchange();
    }

    private MvcTestResult leaderboardAs(String uid, String query) {
        return mvc.get().uri("/api/puzzles/daily/{date}/leaderboard?" + query, today).with(player(uid)).exchange();
    }

    @Test
    void ranksPlayersByTheServersTime() {
        addPlayer("alice", "Alice");
        addPlayer("bob", "Bob");
        solve("bob", 300);
        solve("alice", 200);
        solve("nameless", 400);

        assertThat(leaderboard("difficulty=hard")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {
                  "date": "%s", "difficulty": "hard", "friendsOnly": false, "cleanOnly": false, "total": 3,
                  "entries": [
                    {"rank": 1, "displayName": "Alice", "profileId": null, "elapsedMs": 200000, "mistakes": 1, "late": false, "you": false,
                     "fastestClean": false},
                    {"rank": 2, "displayName": "Bob", "profileId": null, "elapsedMs": 300000, "mistakes": 1, "late": false, "you": false,
                     "fastestClean": false},
                    {"rank": 3, "displayName": null, "profileId": null, "elapsedMs": 400000, "mistakes": 1, "late": false, "you": false,
                     "fastestClean": false}
                  ],
                  "you": null,
                  "fastestClean": null
                }
                """.formatted(today));
    }

    @Test
    void ranksByTheGamesTimerWhenThereIsOne() {
        // Alice was away for most of her hour; her game timer says 13 minutes.
        solve("alice", hard, 3600, 10, false);
        jdbc.update("UPDATE attempts SET active_ms = 780000 WHERE player_id = (SELECT id FROM players WHERE firebase_uid = 'alice')");
        // Bob's solve has no game timer, so his wall-clock time ranks.
        solve("bob", hard, 900, 10, false);

        assertThat(leaderboard("difficulty=hard")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.entries[*].elapsedMs").isEqualTo(List.of(780000, 900000));
            assertThat(json).extractingPath("$.entries[*].rank").isEqualTo(List.of(1, 2));
        });
    }

    @Test
    void highlightsTheFastestSolveWithNoMistakes() {
        solve("alice", hard, 200, 10, false, 3);
        solve("bob", hard, 300, 10, false, 0);
        solve("carol", hard, 400, 10, false, 0);

        assertThat(leaderboard("difficulty=hard")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.fastestClean.elapsedMs").isEqualTo(300000);
            assertThat(json).extractingPath("$.entries[*].fastestClean").isEqualTo(List.of(false, true, false));
        });
    }

    @Test
    void highlightsCanBeBeyondTheTop() {
        for (int i = 0; i < LeaderboardService.TOP; i++) {
            solve("player" + i, hard, 100 + i, 10, false, 1);
        }
        solve("slowButClean", hard, 900, 10, false, 0);

        assertThat(leaderboard("difficulty=hard")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.entries.length()").isEqualTo(LeaderboardService.TOP);
            assertThat(json).extractingPath("$.fastestClean.rank").isEqualTo(LeaderboardService.TOP + 1);
        });
    }

    @Test
    void theCleanBoardRanksOnlySolvesWithNoMistakes() {
        solve("alice", hard, 100, 10, false, 5);
        solve("bob", hard, 300, 10, false, 0);
        solve("carol", hard, 200, 10, false, 0);

        assertThat(leaderboard("difficulty=hard&clean=true")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.cleanOnly").isEqualTo(true);
            assertThat(json).extractingPath("$.total").isEqualTo(2);
            assertThat(json).extractingPath("$.entries[*].elapsedMs").isEqualTo(List.of(200000, 300000));
            assertThat(json).extractingPath("$.entries[*].rank").isEqualTo(List.of(1, 2));
        });
        // The overall board still has the fastest time first, mistakes or not.
        assertThat(leaderboard("difficulty=hard")).bodyJson().extractingPath("$.entries[0].elapsedMs").isEqualTo(100000);
    }

    @Test
    void aCleanReplayAfterASolveWithMistakesDoesntMakeTheCleanBoard() {
        solve("alice", hard, 500, 60, false, 2);
        solve("alice", hard, 100, 5, false, 0);

        assertThat(leaderboard("difficulty=hard&clean=true")).bodyJson().extractingPath("$.total").isEqualTo(0);
    }

    @Test
    void theCleanBoardCanBeFriendsOnly() {
        solve("alice", hard, 200, 10, false, 0);
        solve("bob", hard, 300, 10, false, 0);
        solve("carol", hard, 100, 10, false, 0);
        befriend("alice", "bob");

        assertThat(leaderboardAs("alice", "difficulty=hard&clean=true&friends=true")).bodyJson()
                .extractingPath("$.entries[*].elapsedMs").isEqualTo(List.of(200000, 300000));
    }

    @Test
    void anEmptyLeaderboardHasNoEntries() {
        assertThat(leaderboard("difficulty=hard")).hasStatusOk().bodyJson()
                .extractingPath("$.total").isEqualTo(0);
    }

    @Test
    void onlyEachPlayersFirstSolveCounts() {
        solve("alice", hard, 500, 60, false);
        // A faster replay afterwards doesn't rank.
        solve("alice", hard, 100, 5, false);

        assertThat(leaderboard("difficulty=hard")).bodyJson().extractingPath("$.entries[*].elapsedMs")
                .isEqualTo(List.of(500000));
    }

    @Test
    void equalTimesShareARank() {
        solve("alice", 200);
        solve("bob", 200);
        solve("carol", 300);

        assertThat(leaderboard("difficulty=hard")).bodyJson().extractingPath("$.entries[*].rank")
                .isEqualTo(List.of(1, 1, 3));
    }

    @Test
    void lateSolvesRankWithAMarker() {
        solve("alice", hard, 200, 10, true);

        assertThat(leaderboard("difficulty=hard")).bodyJson().extractingPath("$.entries[0].late").isEqualTo(true);
    }

    @Test
    void unfinishedAttemptsDontRank() {
        addPlayer("alice", null);
        jdbc.update("INSERT INTO attempts (player_id, puzzle_id, started_at) SELECT id, ?, now() FROM players", hard.getId());

        assertThat(leaderboard("difficulty=hard")).bodyJson().extractingPath("$.total").isEqualTo(0);
    }

    @Test
    void aSolveStartedBeforeTheDateBeganAnywhereDoesntRank() {
        // Played from the random pool a week before it became a daily.
        solve("alice", hard, 200, 7 * 24 * 60, false);
        solve("bob", 300);

        assertThat(leaderboard("difficulty=hard")).bodyJson().extractingPath("$.total").isEqualTo(1);
    }

    @Test
    void otherDailiesDontCount() {
        Puzzle easy = dailyPuzzleService.fillWithNew(new DailySlot(today, Difficulty.EASY),
                TestPuzzles.generated(Difficulty.EASY, 2)).getPuzzle();
        solve("alice", easy, 100, 10, false);

        assertThat(leaderboard("difficulty=hard")).bodyJson().extractingPath("$.total").isEqualTo(0);
        assertThat(leaderboard("difficulty=easy")).bodyJson().extractingPath("$.total").isEqualTo(1);
    }

    @Test
    void marksTheViewersOwnSolve() {
        solve("alice", 200);
        solve("bob", 300);

        assertThat(leaderboardAs("bob", "difficulty=hard")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.entries[*].you").isEqualTo(List.of(false, true));
            assertThat(json).extractingPath("$.you.rank").isEqualTo(2);
        });
    }

    @Test
    void includesTheViewerBeyondTheTop() {
        for (int i = 0; i < LeaderboardService.TOP + 5; i++) {
            solve("player" + i, 100 + i);
        }

        assertThat(leaderboardAs("player" + (LeaderboardService.TOP + 2), "difficulty=hard")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.total").isEqualTo(LeaderboardService.TOP + 5);
            assertThat(json).extractingPath("$.entries.length()").isEqualTo(LeaderboardService.TOP);
            assertThat(json).extractingPath("$.you.rank").isEqualTo(LeaderboardService.TOP + 3);
        });
    }

    @Test
    void friendsOnlyRanksTheViewerAndTheirFriends() {
        solve("alice", 200);
        solve("bob", 300);
        solve("carol", 100);
        befriend("alice", "bob");

        assertThat(leaderboardAs("bob", "difficulty=hard&friends=true")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.friendsOnly").isEqualTo(true);
            assertThat(json).extractingPath("$.total").isEqualTo(2);
            assertThat(json).extractingPath("$.entries[*].rank").isEqualTo(List.of(1, 2));
            assertThat(json).extractingPath("$.entries[*].you").isEqualTo(List.of(false, true));
        });
    }

    @Test
    void friendsOnlyNeedsAToken() {
        assertThat(leaderboard("difficulty=hard&friends=true")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aSlotWithoutADailyIsNotFound() {
        assertThat(leaderboard("difficulty=medium")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aDateThatHasntBegunAnywhereIsNotFound() {
        LocalDate later = today.plusDays(3);
        dailyPuzzleService.fillWithNew(new DailySlot(later, Difficulty.HARD), TestPuzzles.generated(Difficulty.HARD, 3));

        assertThat(mvc.get().uri("/api/puzzles/daily/{date}/leaderboard?difficulty=hard", later))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void pastLeaderboardsCanBeRead() {
        LocalDate earlier = today.minusDays(5);
        dailyPuzzleService.fillWithNew(new DailySlot(earlier, Difficulty.HARD), TestPuzzles.generated(Difficulty.HARD, 4));

        assertThat(mvc.get().uri("/api/puzzles/daily/{date}/leaderboard?difficulty=hard", earlier)).hasStatusOk();
    }

    @Test
    void isNotCached() {
        assertThat(leaderboard("difficulty=hard")).headers().hasValue("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
    }

    private MvcTestResult summary() {
        return mvc.get().uri("/api/puzzles/daily/{date}/summary", today).exchange();
    }

    private MvcTestResult summaryAs(String uid) {
        return mvc.get().uri("/api/puzzles/daily/{date}/summary", today).with(player(uid)).exchange();
    }

    @Test
    void summaryCountsSolvesAndNamesTheFastestEasiestFirst() {
        Puzzle easy = dailyPuzzleService.fillWithNew(new DailySlot(today, Difficulty.EASY),
                TestPuzzles.generated(Difficulty.EASY, 2)).getPuzzle();
        addPlayer("alice", "Alice");
        addPlayer("bob", "Bob");
        solve("bob", 300);
        solve("alice", 200);
        solve("alice", easy, 90, 10, false);

        assertThat(summary()).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {
                  "date": "%s",
                  "dailies": [
                    {"difficulty": "easy", "solved": 1, "fastest": {"displayName": "Alice", "elapsedMs": 90000, "you": false},
                     "friendsSolved": null, "friendsFastest": null},
                    {"difficulty": "hard", "solved": 2, "fastest": {"displayName": "Alice", "elapsedMs": 200000, "you": false},
                     "friendsSolved": null, "friendsFastest": null}
                  ]
                }
                """.formatted(today));
    }

    @Test
    void summaryOfADailyNoOneHasSolvedHasNoFastest() {
        assertThat(summary()).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.dailies[0].solved").isEqualTo(0);
            assertThat(json).extractingPath("$.dailies[0].fastest").isNull();
        });
    }

    @Test
    void summaryCountsLikeTheLeaderboard() {
        // Only the first solve counts, and an unfinished attempt or one started before the date began doesn't.
        solve("alice", 200);
        solve("alice", 50);
        addPlayer("bob", null);
        jdbc.update("""
                INSERT INTO attempts (player_id, puzzle_id, started_at)
                SELECT id, ?, now() FROM players WHERE firebase_uid = 'bob'
                """, hard.getId());
        solve("carol", hard, 100, 60 * 24 * 3, false);

        assertThat(summary()).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.dailies[0].solved").isEqualTo(1);
            assertThat(json).extractingPath("$.dailies[0].fastest.elapsedMs").isEqualTo(200000);
        });
    }

    @Test
    void signedInTheSummaryAddsFriends() {
        addPlayer("alice", "Alice");
        addPlayer("bob", "Bob");
        addPlayer("carol", "Carol");
        solve("carol", 100); // the fastest, but not a friend
        solve("alice", 300);
        solve("bob", 200);
        befriend("alice", "bob");

        assertThat(summaryAs("alice")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.dailies[0].solved").isEqualTo(3);
            assertThat(json).extractingPath("$.dailies[0].fastest.displayName").isEqualTo("Carol");
            // Bob is Alice's one friend; the viewer counts for the fastest but not for friendsSolved.
            assertThat(json).extractingPath("$.dailies[0].friendsSolved").isEqualTo(1);
            assertThat(json).extractingPath("$.dailies[0].friendsFastest.displayName").isEqualTo("Bob");
            assertThat(json).extractingPath("$.dailies[0].friendsFastest.you").isEqualTo(false);
        });
        assertThat(summaryAs("bob")).bodyJson()
                .extractingPath("$.dailies[0].friendsFastest.you").isEqualTo(true);
    }

    @Test
    void signedInWithNoFriendsSolvedTheFriendsNumbersAreEmpty() {
        solve("carol", 100);
        addPlayer("dave", "Dave");

        assertThat(summaryAs("dave")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.dailies[0].friendsSolved").isEqualTo(0);
            assertThat(json).extractingPath("$.dailies[0].friendsFastest").isNull();
        });
    }

    @Test
    void theSummaryOfADateThatHasntBegunAnywhereIsNotFound() {
        assertThat(mvc.get().uri("/api/puzzles/daily/{date}/summary", today.plusDays(3))).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theSummaryIsNotCached() {
        assertThat(summary()).headers().hasValue("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
    }

    @Test
    void entriesLinkOnlyToProfilesTheViewerMaySee() {
        // Everyone's own, a public one, and friends' that are shown to friends; never a private one.
        for (String uid : new String[]{"me", "pub", "friendly", "stranger", "hidden"}) {
            addPlayer(uid, uid);
        }
        jdbc.update("UPDATE players SET profile_visibility = 'public' WHERE firebase_uid = 'pub'");
        jdbc.update("UPDATE players SET profile_visibility = 'private' WHERE firebase_uid IN ('hidden', 'me')");
        befriend("me", "friendly");
        befriend("me", "hidden");
        solve("me", 100);
        solve("pub", 200);
        solve("friendly", 300);
        solve("stranger", 400);
        solve("hidden", 500);

        assertThat(leaderboardAs("me", "difficulty=hard")).hasStatusOk().bodyJson()
                .extractingPath("$.entries[*].profileId")
                .isEqualTo(java.util.Arrays.asList(idOf("me"), idOf("pub"), idOf("friendly"), null, null));
        // Signed out, only public profiles.
        assertThat(leaderboard("difficulty=hard")).hasStatusOk().bodyJson()
                .extractingPath("$.entries[*].profileId")
                .isEqualTo(java.util.Arrays.asList(null, idOf("pub"), null, null, null));
    }

    private Integer idOf(String uid) {
        return jdbc.queryForObject("SELECT id FROM players WHERE firebase_uid = ?", Integer.class, uid);
    }
}
