package dev.noahheller.pidoku.pidokuapi.player;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/** A profile visibility that isn't one of {@link ProfileVisibility}'s. Rendered as a 400 problem detail. */
public class InvalidProfileVisibilityException extends ApiException {

    public InvalidProfileVisibilityException(String detail) {
        super(HttpStatus.BAD_REQUEST, detail);
    }
}
