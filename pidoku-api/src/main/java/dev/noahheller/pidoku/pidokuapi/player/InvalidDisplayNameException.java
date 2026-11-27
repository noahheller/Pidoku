package dev.noahheller.pidoku.pidokuapi.player;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/** A display name that isn't allowed (see {@link DisplayName}). Rendered as a 400 problem detail. */
public class InvalidDisplayNameException extends ApiException {

    public InvalidDisplayNameException(String detail) {
        super(HttpStatus.BAD_REQUEST, detail);
    }
}
