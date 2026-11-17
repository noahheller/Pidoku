package dev.noahheller.pidoku.pidokuapi.streak;

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

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class StreakControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    @Autowired
    PuzzleService puzzleService;

    private long seed;

    private Puzzle daily(LocalDate date, Difficulty difficulty) {
        return dailyPuzzleService.fillWithNew(new DailySlot(date, difficulty),
                TestPuzzles.generated(difficulty, seed++)).getPuzzle();
    }

    /**
     * Records a finished attempt directly: the API only completes attempts at the current time, and
     * these need solves on earlier dates.
     *
     * @param solvedAt a timestamptz literal
     */
    private void solve(String uid, Puzzle puzzle, String solvedAt) {
        jdbc.update("INSERT INTO players (firebase_uid) VALUES (?) ON CONFLICT DO NOTHING", uid);
        jdbc.update("""
                INSERT INTO attempts (player_id, puzzle_id, started_at, completed_at, elapsed_ms)
                SELECT id, ?, ?::timestamptz - interval '5 minutes', ?::timestamptz, 300000
                FROM players WHERE firebase_uid = ?
                """, puzzle.getId(), solvedAt, solvedAt, uid);
    }

    /** Solves the date's easy daily at noon UTC on that date. */
    private void solveOn(String uid, LocalDate date) {
        solve(uid, daily(date, Difficulty.EASY), date + "T12:00:00Z");
    }

    private MvcTestResult streak(String uid, LocalDate today) {
        return mvc.get().uri("/api/players/me/streak?today={today}", today).with(player(uid)).exchange();
    }

    @Test
    void aNewPlayerHasNoStreak() {
        assertThat(streak("alice", dailyPuzzleService.today())).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"current": 0, "best": 0, "solvedToday": false}
                """);
    }

    @Test
    void countsConsecutiveDatesWithASolvedDaily() {
        LocalDate today = dailyPuzzleService.today();
        for (int daysAgo = 5; daysAgo >= 3; daysAgo--) {
            solveOn("alice", today.minusDays(daysAgo));
        }
        solveOn("alice", today.minusDays(1));
        solveOn("alice", today);

        assertThat(streak("alice", today)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"current": 2, "best": 3, "solvedToday": true}
                """);
    }

    @Test
    void anyDifficultyCountsAndTwoSolvesOnOneDateCountOnce() {
        LocalDate today = dailyPuzzleService.today();
        solve("alice", daily(today, Difficulty.HARD), today + "T09:00:00Z");
        solve("alice", daily(today, Difficulty.EASY), today + "T10:00:00Z");

        assertThat(streak("alice", today)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"current": 1, "best": 1, "solvedToday": true}
                """);
    }

    @Test
    void solvesCountForThePuzzlesDateWhileItIsTodaySomewhere() {
        LocalDate today = dailyPuzzleService.today();
        // 23:30 UTC the day before is already this date at UTC+14; 11:30 UTC the day after is still
        // this date at UTC-12.
        solve("alice", daily(today.minusDays(1), Difficulty.EASY), today.minusDays(1) + "T11:30:00Z");
        solve("alice", daily(today, Difficulty.EASY), today.minusDays(1) + "T23:30:00Z");

        assertThat(streak("alice", today)).bodyJson().extractingPath("$.current").isEqualTo(2);
    }

    @Test
    void anOldDailySolvedLaterDoesNotFillAMissedDay() {
        LocalDate today = dailyPuzzleService.today();
        solveOn("alice", today.minusDays(3));
        // Opened through its share link two days after its date ended everywhere.
        solve("alice", daily(today.minusDays(2), Difficulty.EASY), today + "T00:30:00Z");
        solveOn("alice", today.minusDays(1));

        assertThat(streak("alice", today)).bodyJson().isStrictlyEqualTo("""
                {"current": 1, "best": 1, "solvedToday": false}
                """);
    }

    @Test
    void aSolveFromBeforeThePuzzleWasADailyDoesNotCount() {
        LocalDate today = dailyPuzzleService.today();
        // Solved from the random pool a week before it was picked as today's daily.
        solve("alice", daily(today, Difficulty.EASY), today.minusDays(7) + "T12:00:00Z");

        assertThat(streak("alice", today)).bodyJson().extractingPath("$.current").isEqualTo(0);
    }

    @Test
    void puzzlesThatArentDailiesDontCount() {
        LocalDate today = dailyPuzzleService.today();
        solve("alice", puzzleService.store(TestPuzzles.generated(Difficulty.EASY), PuzzleSource.POOL), today + "T12:00:00Z");

        assertThat(streak("alice", today)).bodyJson().extractingPath("$.best").isEqualTo(0);
    }

    @Test
    void onlyTheSignedInPlayersSolvesCount() {
        LocalDate today = dailyPuzzleService.today();
        solveOn("bob", today);

        assertThat(streak("alice", today)).bodyJson().extractingPath("$.best").isEqualTo(0);
    }

    @Test
    void aDateThatIsntTodayAnywhereIsABadRequest() {
        assertThat(streak("alice", dailyPuzzleService.today().plusDays(3)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("device's date");
    }

    @Test
    void needsAToken() {
        assertThat(mvc.get().uri("/api/players/me/streak?today={today}", dailyPuzzleService.today()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
