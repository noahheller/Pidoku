package dev.noahheller.pidoku.pidokuapi.attempt;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * No hint can be given: the attempt is already complete, or the solver found no next step.
 * Rendered as a 409 problem detail.
 */
public class NoHintException extends ErrorResponseException {

    public NoHintException(String detail) {
        super(HttpStatus.CONFLICT, ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail), null);
    }
}
