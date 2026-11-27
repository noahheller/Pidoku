package dev.noahheller.pidoku.pidokuapi.puzzle;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/** A specific puzzle was asked for and doesn't exist. Rendered as a 404 problem detail. */
public class PuzzleNotFoundException extends ApiException {

    public PuzzleNotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, detail);
    }

    public static PuzzleNotFoundException forShareCode(String shareCode) {
        return new PuzzleNotFoundException("No puzzle has the share code '" + shareCode + "'.");
    }
}
