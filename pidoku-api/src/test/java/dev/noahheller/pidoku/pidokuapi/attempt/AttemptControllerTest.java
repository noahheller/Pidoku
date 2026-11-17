package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.Grids;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleService;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleSource;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.Arrays;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class AttemptControllerTest extends IntegrationTest {

    private static final String SOLVED = Arrays.toString(Grids.toCells(TestPuzzles.SOLUTION));
    private static final String UNSOLVED = Arrays.toString(Grids.toCells(TestPuzzles.GIVENS));

    @Autowired
    MockMvcTester mvc;

    @Autowired
    PuzzleService puzzleService;

    Puzzle puzzle;

    @BeforeEach
    void storePuzzle() {
        puzzle = puzzleService.store(TestPuzzles.generated(Difficulty.MEDIUM), PuzzleSource.DAILY);
    }

    private MvcTestResult start(String uid, long puzzleId) {
        return mvc.post().uri("/api/attempts").with(player(uid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"puzzleId\": %d}".formatted(puzzleId))
                .exchange();
    }

    /** Starts an attempt and returns its id. */
    private long started(String uid) {
        start(uid, puzzle.getId());
        return jdbc.queryForObject("""
                SELECT a.id FROM attempts a JOIN players p ON p.id = a.player_id
                WHERE p.firebase_uid = ? AND a.completed_at IS NULL
                """, Long.class, uid);
    }

    private MvcTestResult complete(String uid, long attemptId, String grid, int mistakes) {
        return mvc.post().uri("/api/attempts/{id}/complete", attemptId).with(player(uid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grid\": %s, \"mistakes\": %d}".formatted(grid, mistakes))
                .exchange();
    }

    private int attemptCount() {
        return jdbc.queryForObject("SELECT count(*) FROM attempts", Integer.class);
    }

    @Nested
    class Start {

        @Test
        void startsAnUnfinishedAttempt() {
            var json = assertThat(start("alice", puzzle.getId())).hasStatusOk().bodyJson();

            json.extractingPath("$.puzzleId").asNumber().isEqualTo(puzzle.getId().intValue());
            json.extractingPath("$.startedAt").asString().isNotEmpty();
            json.extractingPath("$.completedAt").isNull();
            json.extractingPath("$.elapsedMs").isNull();
        }

        @Test
        void startingAgainContinuesTheSameAttempt() {
            long first = started("alice");
            String firstStartedAt = jdbc.queryForObject("SELECT started_at::text FROM attempts", String.class);

            assertThat(start("alice", puzzle.getId())).hasStatusOk()
                    .bodyJson().extractingPath("$.id").asNumber().isEqualTo((int) first);
            assertThat(attemptCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT started_at::text FROM attempts", String.class))
                    .isEqualTo(firstStartedAt);
        }

        @Test
        void restartingStartsTheSameAttemptsClockAgain() {
            long first = started("alice");
            jdbc.update("UPDATE attempts SET started_at = started_at - interval '90 seconds'");

            assertThat(mvc.post().uri("/api/attempts").with(player("alice"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"puzzleId\": %d, \"restart\": true}".formatted(puzzle.getId())))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.id").asNumber().isEqualTo((int) first);

            assertThat(attemptCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT started_at > now() - interval '60 seconds' FROM attempts", Boolean.class)).isTrue();
        }

        @Test
        void restartingWithNoUnfinishedAttemptJustStartsOne() {
            assertThat(mvc.post().uri("/api/attempts").with(player("alice"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"puzzleId\": %d, \"restart\": true}".formatted(puzzle.getId())))
                    .hasStatusOk();

            assertThat(attemptCount()).isEqualTo(1);
        }

        @Test
        void eachPlayerHasTheirOwnAttempt() {
            started("alice");
            started("bob");

            assertThat(attemptCount()).isEqualTo(2);
        }

        @Test
        void aSolvedPuzzleCanBeStartedAgain() {
            long first = started("alice");
            complete("alice", first, SOLVED, 0);

            assertThat(started("alice")).isNotEqualTo(first);
            assertThat(attemptCount()).isEqualTo(2);
        }

        @Test
        void unknownPuzzleIsNotFound() {
            assertThat(start("alice", 999_999)).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(attemptCount()).isZero();
        }

        @Test
        void missingPuzzleIdIsABadRequest() {
            assertThat(mvc.post().uri("/api/attempts").with(player("alice"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void needsAToken() {
            assertThat(mvc.post().uri("/api/attempts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"puzzleId\": %d}".formatted(puzzle.getId())))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(attemptCount()).isZero();
        }
    }

    @Nested
    class Complete {

        @Test
        void recordsTheTimeSinceTheAttemptStarted() {
            long id = started("alice");
            jdbc.update("UPDATE attempts SET started_at = started_at - interval '90 seconds'");

            var json = assertThat(complete("alice", id, SOLVED, 3)).hasStatusOk().bodyJson();

            json.extractingPath("$.completedAt").asString().isNotEmpty();
            json.extractingPath("$.mistakes").asNumber().isEqualTo(3);
            // Measured by the server: 90 s plus however long the test took.
            json.extractingPath("$.elapsedMs").asNumber()
                    .satisfies(ms -> assertThat(ms.longValue()).isBetween(90_000L, 150_000L));
        }

        @Test
        void aWrongGridIsRejectedAndTheAttemptStaysOpen() {
            long id = started("alice");

            assertThat(complete("alice", id, UNSOLVED, 0)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(complete("alice", id, "[1, 2, 3]", 0)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(jdbc.queryForObject("SELECT completed_at IS NULL FROM attempts", Boolean.class)).isTrue();
        }

        @Test
        void completingTwiceKeepsTheFirstResult() {
            long id = started("alice");
            complete("alice", id, SOLVED, 1);
            String firstCompletedAt = jdbc.queryForObject("SELECT completed_at::text FROM attempts", String.class);

            assertThat(complete("alice", id, SOLVED, 7)).hasStatusOk()
                    .bodyJson().extractingPath("$.mistakes").asNumber().isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT completed_at::text FROM attempts", String.class))
                    .isEqualTo(firstCompletedAt);
        }

        @Test
        void anotherPlayersAttemptIsNotFound() {
            long id = started("alice");

            assertThat(complete("bob", id, SOLVED, 0)).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(jdbc.queryForObject("SELECT completed_at IS NULL FROM attempts", Boolean.class)).isTrue();
        }

        @Test
        void unknownAttemptIsNotFound() {
            assertThat(complete("alice", 999_999, SOLVED, 0)).hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void negativeMistakesAreABadRequest() {
            long id = started("alice");

            assertThat(complete("alice", id, SOLVED, -1)).hasStatus(HttpStatus.BAD_REQUEST);
        }

        @Test
        void needsAToken() {
            long id = started("alice");

            assertThat(mvc.post().uri("/api/attempts/{id}/complete", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"grid\": %s, \"mistakes\": 0}".formatted(SOLVED)))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }
    }
}
