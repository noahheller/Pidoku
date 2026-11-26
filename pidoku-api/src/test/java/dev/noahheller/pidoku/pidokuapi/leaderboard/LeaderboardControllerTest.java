package dev.noahheller.pidoku.pidokuapi.leaderboard;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleService;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleSource;
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
import java.util.Map;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class LeaderboardControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    @Autowired
    PuzzleService puzzleService;

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
                  "date": "%s", "difficulty": "hard", "shareCode": "%s", "friendsOnly": false, "cleanOnly": false, "total": 3,
                  "entries": [
                    {"rank": 1, "displayName": "Alice", "profileId": null, "elapsedMs": 200000, "penaltyMs": 5000, "mistakes": 1, "hintsUsed": 0, "late": false, "you": false,
                     "fastestClean": false, "supporter": false},
                    {"rank": 2, "displayName": "Bob", "profileId": null, "elapsedMs": 300000, "penaltyMs": 5000, "mistakes": 1, "hintsUsed": 0, "late": false, "you": false,
                     "fastestClean": false, "supporter": false},
                    {"rank": 3, "displayName": null, "profileId": null, "elapsedMs": 400000, "penaltyMs": 5000, "mistakes": 1, "hintsUsed": 0, "late": false, "you": false,
                     "fastestClean": false, "supporter": false}
                  ],
                  "you": null,
                  "fastestClean": null
                }
                """.formatted(today, hard.getShareCode()));
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
    void addsTheMistakeAndHintPenalties() {
        // Alice is fastest but guessed: 3 mistakes (+45 s) lose to Bob's clean solve 20 s slower.
        solve("alice", hard, 200, 10, false, 3);
        solve("bob", hard, 220, 10, false, 0);
        // Carol needed the answer to a hint (+90 s).
        solve("carol", hard, 160, 10, false, 0);
        // Dan's single slip costs only 5 s, so he stays ahead of Bob.
        solve("dan", hard, 210, 10, false, 1);
        jdbc.update("""
                UPDATE attempts SET hints_used = 3, hint_penalty_ms = 90000
                WHERE player_id = (SELECT id FROM players WHERE firebase_uid = 'carol')
                """);

        assertThat(leaderboard("difficulty=hard")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.entries[*].elapsedMs").isEqualTo(List.of(210000, 220000, 200000, 160000));
            assertThat(json).extractingPath("$.entries[*].penaltyMs").isEqualTo(List.of(5000, 0, 45000, 90000));
            assertThat(json).extractingPath("$.entries[*].hintsUsed").isEqualTo(List.of(0, 0, 0, 3));
            assertThat(json).extractingPath("$.entries[*].rank").isEqualTo(List.of(1, 2, 3, 4));
        });
    }

    @Test
    void aSolveWithHintsIsntClean() {
        solve("alice", hard, 100, 10, false, 0);
        jdbc.update("UPDATE attempts SET hints_used = 1, hint_penalty_ms = 10000");
        solve("bob", hard, 300, 10, false, 0);

        assertThat(leaderboard("difficulty=hard&clean=true")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.entries[*].elapsedMs").isEqualTo(List.of(300000));
            assertThat(json).extractingPath("$.fastestClean.elapsedMs").isEqualTo(300000);
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
        solve("alice", hard, 100, 10, false, 2);
        solve("bob", hard, 300, 10, false, 0);
        solve("carol", hard, 200, 10, false, 0);

        assertThat(leaderboard("difficulty=hard&clean=true")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.cleanOnly").isEqualTo(true);
            assertThat(json).extractingPath("$.total").isEqualTo(2);
            assertThat(json).extractingPath("$.entries[*].elapsedMs").isEqualTo(List.of(200000, 300000));
            assertThat(json).extractingPath("$.entries[*].rank").isEqualTo(List.of(1, 2));
        });
        // The overall board still ranks her first: 100 s plus 20 s for two mistakes beats 200 s.
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

        // The ranked times: each solve's one mistake adds 5 s.
        assertThat(summary()).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {
                  "date": "%s",
                  "dailies": [
                    {"difficulty": "easy", "solved": 1, "fastest": {"displayName": "Alice", "elapsedMs": 95000, "you": false},
                     "friendsSolved": null, "friendsFastest": null, "yours": null},
                    {"difficulty": "hard", "solved": 2, "fastest": {"displayName": "Alice", "elapsedMs": 205000, "you": false},
                     "friendsSolved": null, "friendsFastest": null, "yours": null}
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
            // 200 s plus 5 s for the solve's one mistake.
            assertThat(json).extractingPath("$.dailies[0].fastest.elapsedMs").isEqualTo(205000);
        });
    }

    @Test
    void summaryRanksWithThePenalties() {
        solve("alice", hard, 200, 10, false, 3);
        solve("bob", hard, 220, 10, false, 0);

        assertThat(summary()).bodyJson()
                .extractingPath("$.dailies[0].fastest.elapsedMs").isEqualTo(220000);
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
    void signedInTheSummaryHasTheViewersOwnFirstSolve() {
        // On another device the daily shows solved, with the time the result showed.
        solve("alice", hard, 200, 30, false, 2);
        solve("alice", hard, 50, 10, false, 0);
        addPlayer("bob", "Bob");

        assertThat(summaryAs("alice")).bodyJson().extractingPath("$.dailies[0].yours").isEqualTo(Map.of(
                "elapsedMs", 200000, "mistakes", 2, "hintsUsed", 0, "hintPenaltyMs", 0,
                "autoNotesUsed", false, "late", false));
        assertThat(summaryAs("bob")).bodyJson().extractingPath("$.dailies[0].yours").isNull();
        assertThat(summary()).bodyJson().extractingPath("$.dailies[0].yours").isNull();
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

    // Leaderboards for any puzzle, by share code (challenge links).

    private MvcTestResult puzzleLeaderboard(String shareCode, String query) {
        return mvc.get().uri("/api/puzzles/{code}/leaderboard?" + query, shareCode).exchange();
    }

    private MvcTestResult puzzleLeaderboardAs(String uid, String shareCode, String query) {
        return mvc.get().uri("/api/puzzles/{code}/leaderboard?" + query, shareCode).with(player(uid)).exchange();
    }

    @Test
    void aPoolPuzzleRanksEveryFirstSolveWithNoDate() {
        Puzzle pool = puzzleService.store(TestPuzzles.generated(Difficulty.EXPERT, 11), PuzzleSource.POOL);
        // No date rule: a solve from a week ago ranks.
        solve("alice", pool, 500, 7 * 24 * 60, false, 0);
        solve("bob", pool, 400, 10, false, 2);
        // Only the first solve counts, as on the daily boards.
        solve("bob", pool, 100, 5, false, 0);

        assertThat(puzzleLeaderboard(pool.getShareCode(), "")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.date").isNull();
            assertThat(json).extractingPath("$.difficulty").isEqualTo("expert");
            assertThat(json).extractingPath("$.shareCode").isEqualTo(pool.getShareCode());
            assertThat(json).extractingPath("$.total").isEqualTo(2);
            // Bob's 400 s plus 20 s for two mistakes still beats Alice's 500 s.
            assertThat(json).extractingPath("$.entries[*].elapsedMs").isEqualTo(List.of(400000, 500000));
            assertThat(json).extractingPath("$.fastestClean.elapsedMs").isEqualTo(500000);
        });
    }

    @Test
    void theShareCodeIsntCaseSensitive() {
        Puzzle pool = puzzleService.store(TestPuzzles.generated(Difficulty.HARD, 12), PuzzleSource.POOL);
        solve("alice", pool, 300, 10, false, 0);

        assertThat(puzzleLeaderboard(pool.getShareCode().toLowerCase(), "")).hasStatusOk().bodyJson()
                .extractingPath("$.total").isEqualTo(1);
    }

    @Test
    void aDailyByShareCodeKeepsItsDateRule() {
        solve("alice", hard, 200, 7 * 24 * 60, false);
        solve("bob", 300);

        assertThat(puzzleLeaderboard(hard.getShareCode(), "")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.date").isEqualTo(today.toString());
            assertThat(json).extractingPath("$.total").isEqualTo(1);
        });
    }

    @Test
    void aPuzzleBoardHasTheSameSwitches() {
        Puzzle pool = puzzleService.store(TestPuzzles.generated(Difficulty.HARD, 13), PuzzleSource.POOL);
        solve("alice", pool, 200, 10, false, 1);
        solve("bob", pool, 300, 10, false, 0);
        solve("carol", pool, 100, 10, false, 0);
        befriend("alice", "bob");

        assertThat(puzzleLeaderboardAs("alice", pool.getShareCode(), "friends=true")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.total").isEqualTo(2);
            assertThat(json).extractingPath("$.you.rank").isEqualTo(1);
        });
        assertThat(puzzleLeaderboard(pool.getShareCode(), "clean=true")).bodyJson()
                .extractingPath("$.entries[*].elapsedMs").isEqualTo(List.of(100000, 300000));
        assertThat(puzzleLeaderboard(pool.getShareCode(), "friends=true")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void marksSupportersWithoutChangingTheirRank() {
        solve("alice", 200);
        solve("bob", 300);
        solve("carol", 400);
        jdbc.update("UPDATE players SET supporter_since = now() - interval '1 day' WHERE firebase_uid = 'bob'");
        // A badge that has run out (a membership, later) no longer shows.
        jdbc.update("UPDATE players SET supporter_since = now() - interval '40 days', supporter_until = now() - interval '1 day' WHERE firebase_uid = 'carol'");

        assertThat(leaderboard("difficulty=hard")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.entries[*].elapsedMs").isEqualTo(List.of(200000, 300000, 400000));
            assertThat(json).extractingPath("$.entries[*].supporter").isEqualTo(List.of(false, true, false));
        });
    }

    @Test
    void anUnknownShareCodeHasNoLeaderboard() {
        assertThat(puzzleLeaderboard("NOSUCH", "")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aFutureDailyByShareCodeHasNoLeaderboard() {
        Puzzle later = dailyPuzzleService.fillWithNew(new DailySlot(today.plusDays(3), Difficulty.HARD),
                TestPuzzles.generated(Difficulty.HARD, 14)).getPuzzle();

        assertThat(puzzleLeaderboard(later.getShareCode(), "")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aPuzzleBoardIsNotCached() {
        assertThat(puzzleLeaderboard(hard.getShareCode(), "")).headers()
                .hasValue("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
    }
}
