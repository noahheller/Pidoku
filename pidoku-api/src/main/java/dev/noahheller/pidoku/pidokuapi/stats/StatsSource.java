package dev.noahheller.pidoku.pidokuapi.stats;

import com.fasterxml.jackson.annotation.JsonValue;
import dev.noahheller.pidoku.pidokuapi.web.ApiIds;

/** Which solves the stats page counts: all of them, dailies only, or random puzzles only. */
public enum StatsSource {
    ALL,
    /** Puzzles that are (or were) a date's daily. */
    DAILY,
    /** Puzzles from the random pool that never became a daily. Shared ones count too. */
    RANDOM;

    /** The id used in the API, e.g. {@code daily}. */
    @JsonValue
    public String id() {
        return ApiIds.of(this);
    }

    /** @throws IllegalArgumentException naming the valid ids, if {@code id} isn't one */
    public static StatsSource fromId(String id) {
        return ApiIds.parse(StatsSource.class, id, "source");
    }
}
