package dev.noahheller.pidoku.pidokuapi.friend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A player tried to add themselves as a friend. Rendered as a 400 problem detail. */
public class OwnFriendCodeException extends ErrorResponseException {

    public OwnFriendCodeException() {
        super(HttpStatus.BAD_REQUEST,
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                        "That's your own friend code. Send it to friends so they can add you."),
                null);
    }
}
