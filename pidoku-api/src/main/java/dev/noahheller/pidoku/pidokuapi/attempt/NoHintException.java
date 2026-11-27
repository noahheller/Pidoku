package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * No hint can be given: the attempt is already complete, or the solver found no next step.
 * Rendered as a 409 problem detail.
 */
public class NoHintException extends ApiException {

    public NoHintException(String detail) {
        super(HttpStatus.CONFLICT, detail);
    }
}
