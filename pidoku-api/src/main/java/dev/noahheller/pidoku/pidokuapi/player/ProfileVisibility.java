package dev.noahheller.pidoku.pidokuapi.player;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Who can see a player's profile. Stored and shown in JSON by {@link #id()} (see the CHECK
 * constraint in V11).
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
        return name().toLowerCase(Locale.ROOT);
    }

    /** @throws InvalidProfileVisibilityException naming the valid ids, if {@code id} isn't one */
    @JsonCreator
    public static ProfileVisibility fromId(String id) {
        for (ProfileVisibility visibility : values()) {
            if (id != null && visibility.id().equalsIgnoreCase(id.strip())) {
                return visibility;
            }
        }
        throw new InvalidProfileVisibilityException("Unknown profile visibility '" + id + "'. Valid values: "
                + Arrays.stream(values()).map(ProfileVisibility::id).collect(Collectors.joining(", ")));
    }

    /** Whether someone with this relationship to the player may see their profile. */
    public boolean visibleTo(boolean self, boolean friend) {
        return self || this == PUBLIC || (this == FRIENDS && friend);
    }
}
