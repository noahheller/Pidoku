package dev.noahheller.pidoku.pidokuapi.player;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A display name that isn't allowed (see {@link DisplayName}). Rendered as a 400 problem detail. */
public class InvalidDisplayNameException extends ErrorResponseException {

    public InvalidDisplayNameException(String detail) {
        super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), null);
    }
}
