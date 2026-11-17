package dev.noahheller.pidoku.pidokuapi.attempt;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * The player has no attempt with this id. Also used for another player's attempt, so ids can't be
 * probed. Rendered as a 404 problem detail.
 */
public class AttemptNotFoundException extends ErrorResponseException {

    public AttemptNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "You have no attempt with the id " + id + "."),
                null);
    }
}
