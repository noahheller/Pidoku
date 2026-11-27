package dev.noahheller.pidoku.pidokuapi.player;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.noahheller.pidoku.pidokuapi.web.ApiIds;

/**
 * Who can see a player's profile, or their puzzle history on it. Stored and shown in JSON by
 * {@link #id()} (see the CHECK constraints in V11 and V14).
 */
public enum ProfileVisibility {
    /** Any player, e.g. from a leaderboard. */
    PUBLIC,
    /** Only their friends. The default. */
    FRIENDS,
    /** No one else. */
    PRIVATE;

    @JsonValue
    public String id() {
        return ApiIds.of(this);
    }

    /** @throws InvalidProfileVisibilityException naming the valid ids, if {@code id} isn't one */
    @JsonCreator
    public static ProfileVisibility fromId(String id) {
        return ApiIds.parse(ProfileVisibility.class, id, "visibility", InvalidProfileVisibilityException::new);
    }

    /** Whether someone with this relationship to the player may see their profile (or history). */
    public boolean visibleTo(boolean self, boolean friend) {
        return self || this == PUBLIC || (this == FRIENDS && friend);
    }
}
