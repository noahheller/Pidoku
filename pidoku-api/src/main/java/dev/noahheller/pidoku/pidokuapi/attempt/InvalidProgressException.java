package dev.noahheller.pidoku.pidokuapi.attempt;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Saved progress that doesn't fit the attempt's puzzle. Rendered as a 400 problem detail. */
public class InvalidProgressException extends ErrorResponseException {

    public InvalidProgressException(String detail) {
        super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), null);
    }
}
