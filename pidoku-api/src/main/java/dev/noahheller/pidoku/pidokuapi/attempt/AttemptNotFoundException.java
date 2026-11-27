package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The player has no attempt with this id. Also used for another player's attempt, so ids can't be
 * probed. Rendered as a 404 problem detail.
 */
public class AttemptNotFoundException extends ApiException {

    public AttemptNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, "You have no attempt with the id " + id + ".");
    }
}
