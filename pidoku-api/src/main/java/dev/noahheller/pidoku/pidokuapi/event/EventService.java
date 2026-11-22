package dev.noahheller.pidoku.pidokuapi.event;

import dev.noahheller.pidoku.pidokuapi.player.Player;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Records analytics events. The frontend queues them and sends them in batches, sometimes on a
 * later visit, so each says when it happened; that's trusted within {@link #MAX_DELAY} and never
 * in the future, like the late completions of attempts.
 */
@Service
public class EventService {

    /** An event claiming to be older than this is recorded as happening this long ago. */
    static final Duration MAX_DELAY = Duration.ofDays(7);
    /** Longest detail, as JSON. Details are a few small fields; this only stops abuse. */
    static final int MAX_DETAIL_LENGTH = 1000;

    /** One event as the frontend sends it. */
    public record NewEvent(EventType type, Long puzzleId, Map<String, Object> detail, Instant occurredAt) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public EventService(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Records the player's events. A puzzle id that doesn't exist is recorded as no puzzle rather
     * than refused, so one stale event doesn't sink the batch.
     *
     * @throws ResponseStatusException 400 if a detail is too long
     */
    @Transactional
    public void record(Player player, List<NewEvent> events) {
        Instant now = clock.instant();
        for (NewEvent event : events) {
            String detail = json.writeValueAsString(event.detail() == null ? Map.of() : event.detail());
            if (detail.length() > MAX_DETAIL_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "An event's detail can be at most " + MAX_DETAIL_LENGTH + " characters of JSON.");
            }
            jdbc.sql("""
                            INSERT INTO events (player_id, type, puzzle_id, detail, created_at)
                            VALUES (:player, :type, (SELECT id FROM puzzles WHERE id = :puzzle), CAST(:detail AS jsonb), :at)
                            """)
                    .param("player", player.getId())
                    .param("type", event.type().id())
                    .param("puzzle", event.puzzleId())
                    .param("detail", detail)
                    .param("at", java.sql.Timestamp.from(occurredAt(event.occurredAt(), now)))
                    .update();
        }
    }

    /** When the event happened, as claimed but never in the future or more than {@link #MAX_DELAY} ago. */
    static Instant occurredAt(Instant claimed, Instant now) {
        if (claimed == null || claimed.isAfter(now)) return now;
        Instant earliest = now.minus(MAX_DELAY);
        return claimed.isBefore(earliest) ? earliest : claimed;
    }
}
