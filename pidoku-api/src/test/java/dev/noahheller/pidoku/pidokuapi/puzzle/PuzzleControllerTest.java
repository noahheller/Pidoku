package dev.noahheller.pidoku.pidokuapi.puzzle;

import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class PuzzleControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    PuzzleService puzzleService;

    private Puzzle store(Difficulty difficulty, PuzzleSource source) {
        return puzzleService.store(TestPuzzles.generated(difficulty), source);
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
                    .contains("'impossible'", "trivial, relaxing, easy, medium, hard, fiendish");
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
