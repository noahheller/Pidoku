package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.Grids;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class HintControllerTest extends IntegrationTest {

    private static final int[] GIVENS = Grids.toCells(TestPuzzles.GIVENS);
    private static final int[] SOLUTION = Grids.toCells(TestPuzzles.SOLUTION);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    MockMvcTester mvc;

    @Autowired
    PuzzleService puzzleService;

    Puzzle puzzle;

    @BeforeEach
    void storePuzzle() {
        puzzle = puzzleService.store(TestPuzzles.generated(Difficulty.MEDIUM), PuzzleSource.POOL);
    }

    private long started(String uid, long puzzleId) {
        mvc.post().uri("/api/attempts").with(player(uid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"puzzleId\": %d}".formatted(puzzleId))
                .exchange();
        return jdbc.queryForObject("""
                SELECT a.id FROM attempts a JOIN players p ON p.id = a.player_id
                WHERE p.firebase_uid = ? AND a.completed_at IS NULL AND a.puzzle_id = ?
                """, Long.class, uid, puzzleId);
    }

    private MvcTestResult hint(String uid, long attemptId, int[] values) {
        return mvc.post().uri("/api/attempts/{id}/hint", attemptId).with(player(uid))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\": %s}".formatted(Arrays.toString(values)))
                .exchange();
    }

    private static JsonNode body(MvcTestResult result) throws Exception {
        assertThat(result).hasStatusOk();
        return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void eachTapRevealsTheNextStepOfTheSameMove() throws Exception {
        long id = started("alice", puzzle.getId());

        JsonNode where = body(hint("alice", id, GIVENS));
        assertThat(where.get("step").asInt()).isEqualTo(1);
        assertThat(where.get("units").isEmpty()).isFalse();
        assertThat(where.has("strategy")).isFalse();
        assertThat(where.has("cell")).isFalse();
        assertThat(where.has("text")).isFalse();
        assertThat(where.get("hintsUsed").asInt()).isEqualTo(1);
        assertThat(where.get("hintPenaltyMs").asLong()).isEqualTo(10_000);

        JsonNode what = body(hint("alice", id, GIVENS));
        assertThat(what.get("step").asInt()).isEqualTo(2);
        assertThat(what.get("strategy").asString()).isNotEmpty();
        assertThat(what.has("pattern")).isTrue();
        assertThat(what.has("cell")).isFalse();
        assertThat(what.has("text")).isFalse();
        assertThat(what.get("hintPenaltyMs").asLong()).isEqualTo(30_000);

        JsonNode answer = body(hint("alice", id, GIVENS));
        assertThat(answer.get("step").asInt()).isEqualTo(3);
        assertThat(answer.get("text").asString()).isNotEmpty();
        assertThat(answer.get("hintsUsed").asInt()).isEqualTo(3);
        assertThat(answer.get("hintPenaltyMs").asLong()).isEqualTo(90_000);
        assertThat(answer.get("units")).isEqualTo(where.get("units"));
        if (answer.has("cell")) {
            int cell = answer.get("cell").asInt();
            assertThat(answer.get("digit").asInt()).isEqualTo(SOLUTION[cell]);
            // Step 2 doesn't give the answer away by marking it as part of the pattern.
            what.get("pattern").forEach(p -> assertThat(p.asInt()).isNotEqualTo(cell));
        }

        // Asking again for the answer already shown costs nothing.
        JsonNode again = body(hint("alice", id, GIVENS));
        assertThat(again.get("step").asInt()).isEqualTo(3);
        assertThat(again.get("hintsUsed").asInt()).isEqualTo(3);
        assertThat(again.get("hintPenaltyMs").asLong()).isEqualTo(90_000);
    }

    @Test
    void aNewMoveStartsAtTheFirstStep() throws Exception {
        long id = started("alice", puzzle.getId());
        hint("alice", id, GIVENS);
        hint("alice", id, GIVENS);
        JsonNode answer = body(hint("alice", id, GIVENS));
        assertThat(answer.has("cell")).as("the first move on this board places a digit").isTrue();

        int[] board = GIVENS.clone();
        board[answer.get("cell").asInt()] = answer.get("digit").asInt();
        JsonNode next = body(hint("alice", id, board));

        assertThat(next.get("step").asInt()).isEqualTo(1);
        assertThat(next.get("hintsUsed").asInt()).isEqualTo(4);
        assertThat(next.get("hintPenaltyMs").asLong()).isEqualTo(100_000);
    }

    @Test
    void aCorrectDigitElsewhereKeepsTheSameMove() throws Exception {
        long id = started("alice", puzzle.getId());
        body(hint("alice", id, GIVENS));
        String key = jdbc.queryForObject("SELECT last_hint->>'key' FROM attempts", String.class);
        int hinted = Integer.parseInt(key.substring(key.indexOf(':') + 1, key.indexOf('=')));

        // The player fills some other cell correctly, then asks again.
        int[] board = GIVENS.clone();
        int elsewhere = -1;
        for (int i = 0; i < board.length && elsewhere < 0; i++) {
            if (board[i] == 0 && i != hinted) elsewhere = i;
        }
        board[elsewhere] = SOLUTION[elsewhere];
        JsonNode next = body(hint("alice", id, board));

        assertThat(next.get("step").asInt()).isEqualTo(2);
        assertThat(next.get("hintPenaltyMs").asLong()).isEqualTo(30_000);
        assertThat(jdbc.queryForObject("SELECT last_hint->>'key' FROM attempts", String.class)).isEqualTo(key);
    }

    @Test
    void followingEveryHintSolvesThePuzzle() throws Exception {
        long id = started("alice", puzzle.getId());
        int[] board = GIVENS.clone();
        int moves = 0;
        while (!Arrays.equals(board, SOLUTION)) {
            assertThat(moves++).as("hints keep making progress").isLessThan(200);
            JsonNode hint;
            do {
                hint = body(hint("alice", id, board));
            } while (hint.get("step").asInt() < 3);
            // An elimination places nothing; the next hint builds on it.
            if (hint.has("cell")) {
                board[hint.get("cell").asInt()] = hint.get("digit").asInt();
            }
        }
    }

    @Test
    void aBoardWithWrongDigitsGetsNoHint() throws Exception {
        long id = started("alice", puzzle.getId());
        int[] board = GIVENS.clone();
        int empty = 1;
        assertThat(board[empty]).isZero();
        board[empty] = SOLUTION[empty] == 1 ? 2 : 1;

        MvcTestResult result = hint("alice", id, board);

        assertThat(result).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.wrongCells").asArray().containsExactly(empty);
        assertThat(jdbc.queryForObject("SELECT hints_used FROM attempts", Integer.class)).isZero();
    }

    @Test
    void aBoardThatChangesTheGivensIsABadRequest() {
        long id = started("alice", puzzle.getId());
        int[] board = GIVENS.clone();
        board[0] = 0;

        assertThat(hint("alice", id, board)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anotherPlayersAttemptIsNotFound() {
        long id = started("alice", puzzle.getId());

        assertThat(hint("bob", id, GIVENS)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aSolvedAttemptGetsNoHint() {
        long id = started("alice", puzzle.getId());
        mvc.post().uri("/api/attempts/{id}/complete", id).with(player("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grid\": %s, \"mistakes\": 0}".formatted(Arrays.toString(SOLUTION)))
                .exchange();

        assertThat(hint("alice", id, GIVENS)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void needsAToken() {
        long id = started("alice", puzzle.getId());

        assertThat(mvc.post().uri("/api/attempts/{id}/hint", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"values\": %s}".formatted(Arrays.toString(GIVENS))))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void completingAddsMistakesToTheHintPenalty() throws Exception {
        long id = started("alice", puzzle.getId());
        hint("alice", id, GIVENS);

        var json = assertThat(mvc.post().uri("/api/attempts/{id}/complete", id).with(player("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"grid\": %s, \"mistakes\": 2}".formatted(Arrays.toString(SOLUTION))))
                .hasStatusOk().bodyJson();

        json.extractingPath("$.hintsUsed").asNumber().isEqualTo(1);
        json.extractingPath("$.hintPenaltyMs").asNumber().isEqualTo(10_000);
        // 5 s x 2² for the mistakes, plus 10 s for the hint.
        json.extractingPath("$.penaltyMs").asNumber().isEqualTo(30_000);
        assertThat(jdbc.queryForObject("SELECT penalty_ms FROM attempts", Long.class)).isEqualTo(30_000L);
    }

    @Test
    void restartingClearsTheHints() {
        long id = started("alice", puzzle.getId());
        hint("alice", id, GIVENS);

        mvc.post().uri("/api/attempts").with(player("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"puzzleId\": %d, \"restart\": true}".formatted(puzzle.getId()))
                .exchange();

        assertThat(jdbc.queryForObject("SELECT hints_used FROM attempts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT hint_penalty_ms FROM attempts", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT last_hint IS NULL FROM attempts", Boolean.class)).isTrue();
    }

    @Test
    void restartingADailyKeepsTheHints() {
        Puzzle daily = puzzleService.store(TestPuzzles.generated(Difficulty.MEDIUM, 7), PuzzleSource.DAILY);
        long id = started("alice", daily.getId());
        hint("alice", id, GIVENS);

        mvc.post().uri("/api/attempts").with(player("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"puzzleId\": %d, \"restart\": true}".formatted(daily.getId()))
                .exchange();

        assertThat(jdbc.queryForObject("SELECT hint_penalty_ms FROM attempts", Long.class)).isEqualTo(10_000L);
    }

    @Test
    void restartingADailyStartsTheNextHintAtStepOne() throws Exception {
        Puzzle daily = puzzleService.store(TestPuzzles.generated(Difficulty.MEDIUM, 7), PuzzleSource.DAILY);
        long id = started("alice", daily.getId());
        hint("alice", id, GIVENS);
        hint("alice", id, GIVENS);

        mvc.post().uri("/api/attempts").with(player("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"puzzleId\": %d, \"restart\": true}".formatted(daily.getId()))
                .exchange();
        JsonNode next = body(hint("alice", id, GIVENS));

        assertThat(next.get("step").asInt()).isEqualTo(1);
        // The 30 s from before the restart stay; the new step adds its own 10 s.
        assertThat(next.get("hintPenaltyMs").asLong()).isEqualTo(40_000);
    }
}
