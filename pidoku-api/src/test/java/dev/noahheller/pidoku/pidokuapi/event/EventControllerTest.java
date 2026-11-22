package dev.noahheller.pidoku.pidokuapi.event;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Duration;
import java.time.Instant;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class EventControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    PuzzleService puzzleService;

    private MvcTestResult post(String uid, String json) {
        return mvc.post().uri("/api/events").with(player(uid))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void recordsABatchForThePlayer() {
        Puzzle puzzle = puzzleService.store(TestPuzzles.generated(Difficulty.HARD), PuzzleSource.POOL);

        assertThat(post("alice", """
                [{"type": "share", "puzzleId": %d, "detail": {"method": "copy", "daily": false}},
                 {"type": "share_arrival", "detail": {"shareCode": "ABCDEFGH", "newVisitor": true}}]
                """.formatted(puzzle.getId()))).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(count("SELECT count(*) FROM events e JOIN players p ON p.id = e.player_id WHERE p.firebase_uid = 'alice'")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT detail ->> 'method' FROM events WHERE type = 'share'", String.class)).isEqualTo("copy");
        assertThat(count("SELECT count(*) FROM events WHERE type = 'share' AND puzzle_id = ?", puzzle.getId())).isOne();
        assertThat(jdbc.queryForObject("SELECT (detail -> 'newVisitor')::boolean FROM events WHERE type = 'share_arrival'", Boolean.class)).isTrue();
    }

    @Test
    void anEventWithoutDetailGetsAnEmptyObject() {
        assertThat(post("alice", "[{\"type\": \"tutorial_completed\"}]")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(jdbc.queryForObject("SELECT detail::text FROM events", String.class)).isEqualTo("{}");
    }

    @Test
    void anUnknownPuzzleIsRecordedAsNone() {
        assertThat(post("alice", "[{\"type\": \"abandoned\", \"puzzleId\": 999999}]")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(count("SELECT count(*) FROM events WHERE puzzle_id IS NULL")).isOne();
    }

    @Test
    void anEventSentLaterKeepsWhenItHappenedWithinAWeek() {
        Instant hourAgo = Instant.now().minus(Duration.ofHours(1));
        Instant monthAgo = Instant.now().minus(Duration.ofDays(30));
        Instant tomorrow = Instant.now().plus(Duration.ofDays(1));

        post("alice", """
                [{"type": "abandoned", "occurredAt": "%s", "detail": {"n": 1}},
                 {"type": "abandoned", "occurredAt": "%s", "detail": {"n": 2}},
                 {"type": "abandoned", "occurredAt": "%s", "detail": {"n": 3}}]
                """.formatted(hourAgo, monthAgo, tomorrow));

        assertThat(jdbc.queryForObject("SELECT created_at < now() - interval '50 minutes' AND created_at > now() - interval '70 minutes' FROM events WHERE detail ->> 'n' = '1'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT created_at BETWEEN now() - interval '7 days 1 minute' AND now() - interval '6 days 23 hours' FROM events WHERE detail ->> 'n' = '2'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT created_at <= now() FROM events WHERE detail ->> 'n' = '3'", Boolean.class)).isTrue();
    }

    @Test
    void refusesAnUnknownType() {
        assertThat(post("alice", "[{\"type\": \"keystroke\"}]")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(count("SELECT count(*) FROM events")).isZero();
    }

    @Test
    void refusesAHugeDetail() {
        String big = "x".repeat(EventService.MAX_DETAIL_LENGTH);
        assertThat(post("alice", "[{\"type\": \"share\", \"detail\": {\"text\": \"" + big + "\"}}]")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(count("SELECT count(*) FROM events")).isZero();
    }

    @Test
    void refusesTooManyAtOnce() {
        String one = "{\"type\": \"share\"}";
        String many = "[" + String.join(",", java.util.Collections.nCopies(EventController.MAX_BATCH + 1, one)) + "]";
        assertThat(post("alice", many)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void needsSignIn() {
        assertThat(mvc.post().uri("/api/events").contentType(MediaType.APPLICATION_JSON).content("[]").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
