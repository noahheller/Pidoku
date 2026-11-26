package dev.noahheller.pidoku.pidokuapi.stats;

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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.LocalDate;
import java.util.List;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class StatsControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    @Autowired
    PuzzleService puzzleService;

    private long seed = 100;

    private Puzzle pool(Difficulty difficulty) {
        return puzzleService.store(TestPuzzles.generated(difficulty, seed++), PuzzleSource.POOL);
    }

    private Puzzle daily(LocalDate date, Difficulty difficulty) {
        return dailyPuzzleService.fillWithNew(new DailySlot(date, difficulty), TestPuzzles.generated(difficulty, seed++)).getPuzzle();
    }

    /** A finished attempt, solved {@code daysAgo} days before now in {@code seconds} on the game's timer. */
    private void solve(String uid, Puzzle puzzle, int seconds, int daysAgo, int mistakes, int hints, boolean autoNotes) {
        jdbc.update("INSERT INTO players (firebase_uid) VALUES (?) ON CONFLICT DO NOTHING", uid);
        jdbc.update("""
                INSERT INTO attempts (player_id, puzzle_id, started_at, completed_at, elapsed_ms, active_ms, mistakes,
                                      hints_used, hint_penalty_ms, auto_notes_used)
                SELECT id, ?, now() - make_interval(days => ?, secs => ?), now() - make_interval(days => ?), ?, ?, ?, ?, ?, ?
                FROM players WHERE firebase_uid = ?
                """, puzzle.getId(), daysAgo, seconds, daysAgo, seconds * 1000L, seconds * 1000L, mistakes,
                hints, hints * 10_000L, autoNotes, uid);
    }

    private void solve(String uid, Puzzle puzzle, int seconds) {
        solve(uid, puzzle, seconds, 0, 0, 0, false);
    }

    private MvcTestResult stats(String uid, String query) {
        return mvc.get().uri("/api/players/me/stats?today={today}&" + query, dailyPuzzleService.today())
                .with(player(uid)).exchange();
    }

    @Test
    void aNewPlayerHasNoStats() {
        assertThat(stats("alice", "")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"source": "all", "includeAssisted": false, "currentStreak": 0, "bestStreak": 0, "dailiesSolved": 0,
                 "difficulties": []}
                """);
    }

    @Test
    void summarisesEachDifficultyEasiestFirst() {
        solve("alice", pool(Difficulty.HARD), 600);
        solve("alice", pool(Difficulty.EASY), 100);
        solve("alice", pool(Difficulty.EASY), 300, 0, 2, 0, false);
        solve("alice", pool(Difficulty.EASY), 200);

        assertThat(stats("alice", "")).hasStatusOk().bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.difficulties[*].difficulty").isEqualTo(List.of("easy", "hard"));
            assertThat(json).extractingPath("$.difficulties[0]").isEqualTo(java.util.Map.of(
                    "difficulty", "easy", "solved", 3, "assisted", 0,
                    // 300 s plus 20 s for two mistakes.
                    "averageMs", 206667, "medianMs", 200000, "bestMs", 100000,
                    "cleanSolves", 2, "averageMistakes", 0.6666666666666666,
                    "recentSolved", 3, "recentAverageMs", 206667));
        });
    }

    @Test
    void theMedianOfAnEvenCountIsTheMeanOfTheMiddleTwo() {
        for (int seconds : new int[]{100, 200, 400, 900}) {
            solve("alice", pool(Difficulty.MEDIUM), seconds);
        }

        assertThat(stats("alice", "")).bodyJson().extractingPath("$.difficulties[0].medianMs").isEqualTo(300000);
    }

    @Test
    void onlyTheFirstSolveOfAPuzzleCounts() {
        Puzzle puzzle = pool(Difficulty.EASY);
        solve("alice", puzzle, 500, 2, 0, 0, false);
        solve("alice", puzzle, 50, 1, 0, 0, false);

        assertThat(stats("alice", "")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.difficulties[0].solved").isEqualTo(1);
            assertThat(json).extractingPath("$.difficulties[0].bestMs").isEqualTo(500000);
        });
    }

    @Test
    void unfinishedAttemptsAndOtherPlayersDontCount() {
        Puzzle puzzle = pool(Difficulty.EASY);
        solve("bob", puzzle, 100);
        jdbc.update("INSERT INTO players (firebase_uid) VALUES ('alice')");
        jdbc.update("INSERT INTO attempts (player_id, puzzle_id, started_at) SELECT id, ?, now() FROM players WHERE firebase_uid = 'alice'",
                pool(Difficulty.HARD).getId());

        assertThat(stats("alice", "")).bodyJson().extractingPath("$.difficulties").isEqualTo(List.of());
    }

    @Test
    void assistedSolvesAreLeftOutUnlessAskedFor() {
        solve("alice", pool(Difficulty.HARD), 300);
        solve("alice", pool(Difficulty.HARD), 100, 0, 0, 2, false);
        solve("alice", pool(Difficulty.HARD), 150, 0, 0, 0, true);

        assertThat(stats("alice", "")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.includeAssisted").isEqualTo(false);
            assertThat(json).extractingPath("$.difficulties[0].solved").isEqualTo(1);
            assertThat(json).extractingPath("$.difficulties[0].assisted").isEqualTo(2);
            assertThat(json).extractingPath("$.difficulties[0].bestMs").isEqualTo(300000);
        });
        assertThat(stats("alice", "assisted=true")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.difficulties[0].solved").isEqualTo(3);
            // 100 s plus 20 s for two hint steps.
            assertThat(json).extractingPath("$.difficulties[0].bestMs").isEqualTo(120000);
            // A hint makes a solve not clean; auto notes don't.
            assertThat(json).extractingPath("$.difficulties[0].cleanSolves").isEqualTo(2);
        });
    }

    @Test
    void aDifficultyWithOnlyAssistedSolvesStillShowsWithNoTimes() {
        solve("alice", pool(Difficulty.EXPERT), 100, 0, 0, 1, false);

        assertThat(stats("alice", "")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.difficulties[0].solved").isEqualTo(0);
            assertThat(json).extractingPath("$.difficulties[0].assisted").isEqualTo(1);
            assertThat(json).extractingPath("$.difficulties[0].averageMs").isNull();
            assertThat(json).extractingPath("$.difficulties[0].averageMistakes").isEqualTo(0.0);
        });
    }

    @Test
    void filtersBySource() {
        LocalDate today = dailyPuzzleService.today();
        solve("alice", daily(today, Difficulty.EASY), 100);
        solve("alice", pool(Difficulty.EASY), 300);

        assertThat(stats("alice", "source=daily")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.source").isEqualTo("daily");
            assertThat(json).extractingPath("$.difficulties[0].solved").isEqualTo(1);
            assertThat(json).extractingPath("$.difficulties[0].bestMs").isEqualTo(100000);
        });
        assertThat(stats("alice", "source=random")).bodyJson()
                .extractingPath("$.difficulties[0].bestMs").isEqualTo(300000);
        assertThat(stats("alice", "source=all")).bodyJson()
                .extractingPath("$.difficulties[0].solved").isEqualTo(2);
        // The daily count and the streak don't depend on the filter.
        assertThat(stats("alice", "source=random")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.dailiesSolved").isEqualTo(1);
            assertThat(json).extractingPath("$.currentStreak").isEqualTo(1);
        });
    }

    @Test
    void aDailyPlayedBeforeItsDateBeganDoesntCount() {
        // Played from the pool a week before it became today's daily.
        solve("alice", daily(dailyPuzzleService.today(), Difficulty.HARD), 200, 7, 0, 0, false);

        assertThat(stats("alice", "")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.difficulties").isEqualTo(List.of());
            assertThat(json).extractingPath("$.dailiesSolved").isEqualTo(0);
        });
    }

    @Test
    void theTrendIsTheLast30Days() {
        solve("alice", pool(Difficulty.MEDIUM), 600, 40, 0, 0, false);
        solve("alice", pool(Difficulty.MEDIUM), 300, 3, 0, 0, false);

        assertThat(stats("alice", "")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.difficulties[0].averageMs").isEqualTo(450000);
            assertThat(json).extractingPath("$.difficulties[0].recentSolved").isEqualTo(1);
            assertThat(json).extractingPath("$.difficulties[0].recentAverageMs").isEqualTo(300000);
        });
    }

    @Test
    void anUnknownSourceIsABadRequest() {
        assertThat(stats("alice", "source=campaign")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void needsSignIn() {
        assertThat(mvc.get().uri("/api/players/me/stats?today={today}", dailyPuzzleService.today()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
