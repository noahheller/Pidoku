package dev.noahheller.pidoku.pidokuapi.stats;

import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.Arrays;
import java.util.Locale;

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
        return name().toLowerCase(Locale.ROOT);
    }

    /** @throws ErrorResponseException (400) if {@code id} isn't one of the ids */
    public static StatsSource fromId(String id) {
        return Arrays.stream(values())
                .filter(source -> source.id().equalsIgnoreCase(id.trim()))
                .findFirst()
                .orElseThrow(() -> new ErrorResponseException(HttpStatus.BAD_REQUEST,
                        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                                "source must be all, daily or random, not \"" + id + "\"."), null));
    }
}
