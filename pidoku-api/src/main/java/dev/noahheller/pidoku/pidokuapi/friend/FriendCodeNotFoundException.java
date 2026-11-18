package dev.noahheller.pidoku.pidokuapi.friend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** No player has the friend code. Rendered as a 404 problem detail. */
public class FriendCodeNotFoundException extends ErrorResponseException {

    public FriendCodeNotFoundException(String code) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                        "No player has the friend code '" + code + "'. Check it and try again."),
                null);
    }
}
