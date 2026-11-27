package dev.noahheller.pidoku.pidokuapi.puzzle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import dev.noahheller.pidoku.pidokuapi.web.ApiIds;

/**
 * The difficulties players can choose from, easiest first.
 *
 * <p>Stored in the database by {@link #name()} (see the CHECK constraint in V1); shown in URLs and
 * JSON by {@link #id()}, e.g. {@code /api/puzzles/random?difficulty=hard}. Adding a value needs a
 * migration that widens the constraint, and an engine profile in {@code EnginePuzzleGenerator}.
 */
public enum Difficulty {
    TRIVIAL,
    EASY,
    MEDIUM,
    HARD,
    EXPERT,
    FIENDISH;

    /** Lower-case name used in the API. */
    @JsonValue
    public String id() {
        return ApiIds.of(this);
    }

    /**
     * Parses an API id, ignoring case.
     *
     * @throws IllegalArgumentException naming the valid ids, if {@code id} isn't one
     */
    @JsonCreator
    public static Difficulty fromId(String id) {
        return ApiIds.parse(Difficulty.class, id, "difficulty");
    }
}
