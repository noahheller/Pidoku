package dev.noahheller.pidoku.pidokuapi.player;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A profile visibility that isn't one of {@link ProfileVisibility}'s. Rendered as a 400 problem detail. */
public class InvalidProfileVisibilityException extends ErrorResponseException {

    public InvalidProfileVisibilityException(String detail) {
        super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), null);
    }
}
