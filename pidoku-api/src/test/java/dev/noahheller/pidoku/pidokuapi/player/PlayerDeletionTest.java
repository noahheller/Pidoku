package dev.noahheller.pidoku.pidokuapi.player;

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

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class PlayerDeletionTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    Puzzle puzzle;

    @BeforeEach
    void storePuzzle() {
        puzzle = dailyPuzzleService.fillWithNew(new DailySlot(dailyPuzzleService.today(), Difficulty.EASY), TestPuzzles.generated(Difficulty.EASY)).getPuzzle();
    }

    private void addPlayer(String uid) {
        jdbc.update("INSERT INTO players (firebase_uid, display_name) VALUES (?, ?) ON CONFLICT DO NOTHING", uid, uid);
    }

    private void solve(String uid) {
        addPlayer(uid);
        jdbc.update("""
                INSERT INTO attempts (player_id, puzzle_id, started_at, completed_at, elapsed_ms)
                SELECT id, ?, now() - interval '2 minutes', now(), 120000 FROM players WHERE firebase_uid = ?
                """, puzzle.getId(), uid);
    }

    private void befriend(String a, String b) {
        jdbc.update("""
                INSERT INTO friendships (player_id, friend_id)
                SELECT x.id, y.id FROM players x, players y
                WHERE (x.firebase_uid, y.firebase_uid) IN ((?, ?), (?, ?))
                """, a, b, b, a);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private MvcTestResult delete(String uid) {
        return mvc.delete().uri("/api/players/me").with(player(uid)).exchange();
    }

    @Test
    void deletesThePlayerTheirAttemptsAndFriendshipsBothWays() {
        solve("alice");
        solve("bob");
        befriend("alice", "bob");

        assertThat(delete("alice")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(count("SELECT count(*) FROM players WHERE firebase_uid = 'alice'")).isZero();
        assertThat(count("SELECT count(*) FROM friendships")).isZero();
        assertThat(count("SELECT count(*) FROM attempts a JOIN players p ON p.id = a.player_id WHERE p.firebase_uid = 'bob'")).isOne();
        assertThat(count("SELECT count(*) FROM attempts")).isOne();
    }

    @Test
    void leavesOtherPlayersAlone() {
        solve("alice");
        solve("bob");
        solve("carol");
        befriend("bob", "carol");

        assertThat(delete("alice")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(count("SELECT count(*) FROM players")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM attempts")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM friendships")).isEqualTo(2);
    }

    @Test
    void succeedsWhenThereIsNoPlayer() {
        assertThat(delete("nobody")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(delete("nobody")).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(count("SELECT count(*) FROM players")).isZero();
    }

    @Test
    void theSameSignInStartsOverAsANewPlayer() {
        solve("alice");
        long before = jdbc.queryForObject("SELECT id FROM players WHERE firebase_uid = 'alice'", Long.class);

        delete("alice");

        assertThat(mvc.get().uri("/api/players/me").with(player("alice")))
                .hasStatusOk()
                .bodyJson().extractingPath("$.displayName").isNull();
        assertThat(jdbc.queryForObject("SELECT id FROM players WHERE firebase_uid = 'alice'", Long.class)).isNotEqualTo(before);
    }

    @Test
    void needsSignIn() {
        assertThat(mvc.delete().uri("/api/players/me")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
