package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/** Saved progress that doesn't fit the attempt's puzzle. Rendered as a 400 problem detail. */
public class InvalidProgressException extends ApiException {

    public InvalidProgressException(String detail) {
        super(HttpStatus.BAD_REQUEST, detail);
    }
}
