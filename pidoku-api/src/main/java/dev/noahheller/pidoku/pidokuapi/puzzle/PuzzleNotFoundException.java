package dev.noahheller.pidoku.pidokuapi.puzzle;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A specific puzzle was asked for and doesn't exist. Rendered as a 404 problem detail. */
public class PuzzleNotFoundException extends ErrorResponseException {

    public PuzzleNotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail), null);
    }

    public static PuzzleNotFoundException forShareCode(String shareCode) {
        return new PuzzleNotFoundException("No puzzle has the share code '" + shareCode + "'.");
    }
}
