package dev.noahheller.pidoku.pidokuapi.event;

import dev.noahheller.pidoku.pidokuapi.player.PlayerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Analytics events from the frontend. Needs a Firebase ID token, like attempts. */
@RestController
public class EventController {

    /** Most events one request may carry; the frontend sends what it has queued. */
    static final int MAX_BATCH = 50;

    private final EventService eventService;
    private final PlayerService playerService;

    public EventController(EventService eventService, PlayerService playerService) {
        this.eventService = eventService;
        this.playerService = playerService;
    }

    /**
     * @param type       what happened (see {@link EventType})
     * @param puzzleId   the puzzle it happened on, if any
     * @param detail     a small JSON object, depending on the type; may be left out
     * @param occurredAt when it happened, for an event sent later; left out means now
     */
    public record EventRequest(@NotNull EventType type, Long puzzleId, Map<String, Object> detail, Instant occurredAt) {
    }

    /** Records a batch of the player's events. 204 with no body. */
    @PostMapping("/api/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@AuthenticationPrincipal Jwt token, @RequestBody @Size(max = MAX_BATCH) List<@Valid @NotNull EventRequest> events) {
        if (events.isEmpty()) return;
        eventService.record(playerService.forToken(token), events.stream()
                .map(e -> new EventService.NewEvent(e.type(), e.puzzleId(), e.detail(), e.occurredAt()))
                .toList());
    }
}
