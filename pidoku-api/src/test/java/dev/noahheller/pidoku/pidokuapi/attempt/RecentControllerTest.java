package dev.noahheller.pidoku.pidokuapi.attempt;

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

import java.util.ArrayList;
import java.util.List;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class RecentControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    PuzzleService puzzleService;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    private long playerId(String uid) {
        jdbc.update("INSERT INTO players (firebase_uid) VALUES (?) ON CONFLICT DO NOTHING", uid);
        return jdbc.queryForObject("SELECT id FROM players WHERE firebase_uid = ?", Long.class, uid);
    }

    private Puzzle pool(long seed) {
        return puzzleService.store(TestPuzzles.generated(Difficulty.HARD, seed), PuzzleSource.POOL);
    }

    /** An attempt started {@code minutesAgo}, solved in {@code solvedInMs} or left unfinished (null). */
    private void attempt(String uid, Puzzle puzzle, int minutesAgo, Long solvedInMs) {
        long player = playerId(uid);
        if (solvedInMs == null) {
            jdbc.update("INSERT INTO attempts (player_id, puzzle_id, started_at) VALUES (?, ?, now() - make_interval(mins => ?))",
                    player, puzzle.getId(), minutesAgo);
        } else {
            jdbc.update("""
                    INSERT INTO attempts (player_id, puzzle_id, started_at, completed_at, elapsed_ms, active_ms, mistakes)
                    VALUES (?, ?, now() - make_interval(mins => ?), now() - make_interval(mins => ?), ?, ?, 1)
                    """, player, puzzle.getId(), minutesAgo, minutesAgo - 1, solvedInMs + 5000, solvedInMs);
        }
    }

    @Test
    void unfinishedPuzzlesComeFirstThenTheLatestSolves() {
        Puzzle oldSolve = pool(1);
        Puzzle newSolve = pool(2);
        Puzzle unfinished = pool(3);
        attempt("alice", oldSolve, 60, 100_000L);
        attempt("alice", unfinished, 50, null);
        attempt("alice", newSolve, 10, 200_000L);

        var json = assertThat(mvc.get().uri("/api/players/me/recent").with(player("alice"))).hasStatusOk().bodyJson();

        json.extractingPath("$[*].puzzle.shareCode").asArray()
                .containsExactly(unfinished.getShareCode(), newSolve.getShareCode(), oldSolve.getShareCode());
        json.extractingPath("$[0].completed").isEqualTo(false);
        json.extractingPath("$[0].timeMs").isNull();
        json.extractingPath("$[1].completed").isEqualTo(true);
        json.extractingPath("$[1].timeMs").asNumber().isEqualTo(200_000);
        json.extractingPath("$[1].mistakes").asNumber().isEqualTo(1);
    }

    @Test
    void anUnfinishedPuzzleShowsItsSavedTime() {
        Puzzle puzzle = pool(1);
        attempt("alice", puzzle, 5, null);
        jdbc.update("""
                UPDATE attempts SET progress = '{"values": [], "notes": [], "mistakes": 3, "activeMs": 42000}', progress_updated_at = now()
                """);

        var json = assertThat(mvc.get().uri("/api/players/me/recent").with(player("alice"))).hasStatusOk().bodyJson();

        json.extractingPath("$[0].timeMs").asNumber().isEqualTo(42000);
        json.extractingPath("$[0].mistakes").asNumber().isEqualTo(3);
    }

    @Test
    void aReplayInProgressShowsAsUnfinished() {
        Puzzle puzzle = pool(1);
        attempt("alice", puzzle, 60, 100_000L);
        attempt("alice", puzzle, 5, null);

        var json = assertThat(mvc.get().uri("/api/players/me/recent").with(player("alice"))).hasStatusOk().bodyJson();

        json.extractingPath("$").asArray().hasSize(1);
        json.extractingPath("$[0].completed").isEqualTo(false);
    }

    @Test
    void dailiesCarryTheirDate() {
        DailySlot slot = new DailySlot(dailyPuzzleService.today(), Difficulty.EASY);
        Puzzle daily = dailyPuzzleService.fillWithNew(slot, TestPuzzles.generated(Difficulty.EASY)).getPuzzle();
        attempt("alice", daily, 5, 90_000L);

        var json = assertThat(mvc.get().uri("/api/players/me/recent").with(player("alice"))).hasStatusOk().bodyJson();

        json.extractingPath("$[0].puzzle.dailyDate").asString().isEqualTo(slot.date().toString());
    }

    @Test
    void holdsOnlyTheLatestTen() {
        List<Puzzle> puzzles = new ArrayList<>();
        for (int i = 0; i < RecentService.LIMIT + 2; i++) {
            Puzzle puzzle = pool(i);
            puzzles.add(puzzle);
            attempt("alice", puzzle, 100 - i, 60_000L);
        }

        var json = assertThat(mvc.get().uri("/api/players/me/recent").with(player("alice"))).hasStatusOk().bodyJson();

        json.extractingPath("$").asArray().hasSize(RecentService.LIMIT);
        json.extractingPath("$[0].puzzle.shareCode").isEqualTo(puzzles.getLast().getShareCode());
    }

    @Test
    void onlyThePlayersOwn() {
        attempt("bob", pool(1), 5, 60_000L);

        assertThat(mvc.get().uri("/api/players/me/recent").with(player("alice"))).hasStatusOk()
                .bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void needsAToken() {
        assertThat(mvc.get().uri("/api/players/me/recent")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
