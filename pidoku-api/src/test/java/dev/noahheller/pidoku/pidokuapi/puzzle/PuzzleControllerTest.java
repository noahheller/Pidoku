package dev.noahheller.pidoku.pidokuapi.puzzle;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDate;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class PuzzleControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    PuzzleService puzzleService;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    private Puzzle store(Difficulty difficulty, PuzzleSource source) {
        return puzzleService.store(TestPuzzles.generated(difficulty), source);
    }

    private void startAttempt(String uid, Puzzle puzzle) {
        assertThat(mvc.post().uri("/api/attempts").with(player(uid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"puzzleId\": %d}".formatted(puzzle.getId())))
                .hasStatusOk();
    }

    @Nested
    class Random {

        @Test
        void returnsAPoolPuzzleOfTheRequestedDifficulty() {
            Puzzle stored = store(Difficulty.HARD, PuzzleSource.POOL);
            store(Difficulty.EASY, PuzzleSource.POOL);

            assertThat(mvc.get().uri("/api/puzzles/random?difficulty=hard"))
                    .hasStatusOk()
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {
                              "id": %d,
                              "shareCode": "%s",
                              "difficulty": "hard",
                              "size": 9,
                              "clueCount": 31,
                              "score": 92,
                              "dependency": 4.0,
                              "hardestStrategy": "INTERSECTION_REMOVAL"
                            }
                            """.formatted(stored.getId(), stored.getShareCode()));
        }

        @Test
        void sendsGridsAsFlatArrays() {
            store(Difficulty.HARD, PuzzleSource.POOL);

            var json = assertThat(mvc.get().uri("/api/puzzles/random?difficulty=hard")).bodyJson();
            json.extractingPath("$.givens").asArray().hasSize(81).startsWith(3, 0, 1, 0, 1, 9);
            json.extractingPath("$.solution").asArray().hasSize(81).startsWith(3, 2, 1, 4, 1, 9);
        }

        @Test
        void difficultyIgnoresCase() {
            store(Difficulty.HARD, PuzzleSource.POOL);

            assertThat(mvc.get().uri("/api/puzzles/random?difficulty=HaRd")).hasStatusOk();
        }

        @Test
        void neverServesDailyCampaignOrCustomPuzzles() {
            store(Difficulty.HARD, PuzzleSource.DAILY);
            store(Difficulty.HARD, PuzzleSource.CAMPAIGN);
            store(Difficulty.HARD, PuzzleSource.CUSTOM);

            assertThat(mvc.get().uri("/api/puzzles/random?difficulty=hard"))
                    .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @Test
        void skipsPuzzlesTheSignedInPlayerHasStarted() {
            Puzzle seen = store(Difficulty.HARD, PuzzleSource.POOL);
            Puzzle unseen = store(Difficulty.HARD, PuzzleSource.POOL);
            startAttempt("alice", seen);

            for (int i = 0; i < 10; i++) {
                assertThat(mvc.get().uri("/api/puzzles/random?difficulty=hard").with(player("alice")))
                        .hasStatusOk()
                        .bodyJson().extractingPath("$.id").asNumber().isEqualTo(unseen.getId().intValue());
            }
        }

        @Test
        void aPuzzleTheyHaventStartedIsNotARepeat() {
            store(Difficulty.HARD, PuzzleSource.POOL);
            startAttempt("alice", store(Difficulty.EASY, PuzzleSource.POOL));

            assertThat(mvc.get().uri("/api/puzzles/random?difficulty=hard").with(player("alice")))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.repeat").isEqualTo(false);
        }

        @Test
        void onceThePlayerHasStartedThemAllRepeatsTheOneStartedLongestAgo() {
            Puzzle older = store(Difficulty.HARD, PuzzleSource.POOL);
            Puzzle newer = store(Difficulty.HARD, PuzzleSource.POOL);
            startAttempt("alice", older);
            startAttempt("alice", newer);
            jdbc.update("UPDATE attempts SET started_at = started_at - interval '1 day' WHERE puzzle_id = ?", older.getId());

            var json = assertThat(mvc.get().uri("/api/puzzles/random?difficulty=hard").with(player("alice")))
                    .hasStatusOk().bodyJson();
            json.extractingPath("$.id").asNumber().isEqualTo(older.getId().intValue());
            json.extractingPath("$.repeat").isEqualTo(true);
            json.extractingPath("$.shareCode").isEqualTo(older.getShareCode());
        }

        @Test
        void signedOutItIsNeverMarkedARepeat() {
            store(Difficulty.HARD, PuzzleSource.POOL);

            assertThat(mvc.get().uri("/api/puzzles/random?difficulty=hard"))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.repeat").isEqualTo(false);
        }

        @Test
        void aSignedInPlayerWhoHasPlayedNothingGetsAnyPuzzleAndNoPlayerRow() {
            store(Difficulty.HARD, PuzzleSource.POOL);

            assertThat(mvc.get().uri("/api/puzzles/random?difficulty=hard").with(player("newcomer"))).hasStatusOk();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM players", Integer.class)).isZero();
        }

        @Test
        void emptyPoolIsServiceUnavailable() {
            assertThat(mvc.get().uri("/api/puzzles/random?difficulty=fiendish"))
                    .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .bodyJson()
                    .extractingPath("$.detail").asString().contains("fiendish");
        }

        @Test
        void unknownDifficultyIsABadRequestThatListsTheValidOnes() {
            assertThat(mvc.get().uri("/api/puzzles/random?difficulty=impossible"))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.detail").asString()
                    .contains("'impossible'", "trivial, easy, medium, hard, expert, fiendish");
        }

        @Test
        void missingDifficultyIsABadRequest() {
            assertThat(mvc.get().uri("/api/puzzles/random")).hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class ByShareCode {

        @Test
        void returnsThePuzzle() {
            Puzzle stored = store(Difficulty.MEDIUM, PuzzleSource.DAILY);

            assertThat(mvc.get().uri("/api/puzzles/{code}", stored.getShareCode()))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.id").asNumber().isEqualTo(stored.getId().intValue());
        }

        @Test
        void saysWhichDateADailyIsFor() {
            LocalDate date = dailyPuzzleService.today();
            Puzzle daily = dailyPuzzleService.fillWithNew(new DailySlot(date, Difficulty.HARD),
                    TestPuzzles.generated(Difficulty.HARD)).getPuzzle();

            assertThat(mvc.get().uri("/api/puzzles/{code}", daily.getShareCode()))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.dailyDate").isEqualTo(date.toString());
        }

        @Test
        void aPuzzleThatIsntADailyHasNoDailyDate() {
            Puzzle stored = store(Difficulty.MEDIUM, PuzzleSource.POOL);

            assertThat(mvc.get().uri("/api/puzzles/{code}", stored.getShareCode()))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.dailyDate").isNull();
        }

        @Test
        void ignoresCase() {
            Puzzle stored = store(Difficulty.MEDIUM, PuzzleSource.POOL);

            assertThat(mvc.get().uri("/api/puzzles/{code}", stored.getShareCode().toLowerCase()))
                    .hasStatusOk();
        }

        @Test
        void unknownCodeIsNotFound() {
            assertThat(mvc.get().uri("/api/puzzles/ZZZZZZZZ"))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson()
                    .extractingPath("$.detail").asString().contains("ZZZZZZZZ");
        }
    }
}
