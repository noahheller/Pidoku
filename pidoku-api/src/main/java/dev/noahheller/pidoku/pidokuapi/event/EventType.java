package dev.noahheller.pidoku.pidokuapi.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.noahheller.pidoku.pidokuapi.web.ApiIds;

/**
 * What an analytics event records (table {@code events}, V8). Each answers a question in
 * docs/ROADMAP.md ("Monetisation and analytics"); don't add one that doesn't. Stored and sent by
 * {@link #id()}, e.g. {@code "share_arrival"}.
 */
public enum EventType {
    /** The player tapped Share or Copy on a result. Detail: {@code method} ("share" or "copy"), {@code daily}. */
    SHARE,
    /**
     * The player opened a share link ({@code ?ref=share}). Detail: {@code shareCode}, and
     * {@code newVisitor}, true when this browser had never signed in before: a player the link brought.
     */
    SHARE_ARRIVAL,
    /**
     * The player left an unfinished puzzle (back to Puzzles, or closed the tab); they may come back
     * to it. Detail: {@code filled} (cells they'd filled), {@code empty} (cells still empty),
     * {@code activeMs}, {@code mistakes}.
     */
    ABANDONED,
    /** The player finished the tutorial. Detail: {@code mistakes}. */
    TUTORIAL_COMPLETED,
    /** The player skipped the tutorial. Detail: {@code step}, the step they were on (0 for the first). */
    TUTORIAL_SKIPPED;

    @JsonValue
    public String id() {
        return ApiIds.of(this);
    }

    /** @throws IllegalArgumentException naming the valid ids, if {@code id} isn't one */
    @JsonCreator
    public static EventType fromId(String id) {
        return ApiIds.parse(EventType.class, id, "event type");
    }
}
