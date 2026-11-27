package dev.noahheller.pidoku.pidokuapi.player;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A merge of an anonymous player into a Google one that isn't allowed: the anonymous token didn't
 * verify or isn't anonymous, or the signed-in account isn't Google's. Rendered as a 400 problem detail.
 */
public class InvalidMergeException extends ApiException {

    public InvalidMergeException(String detail) {
        super(HttpStatus.BAD_REQUEST, detail);
    }
}
