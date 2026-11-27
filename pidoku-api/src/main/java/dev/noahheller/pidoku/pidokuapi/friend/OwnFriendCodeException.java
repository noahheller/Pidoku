package dev.noahheller.pidoku.pidokuapi.friend;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/** A player tried to add themselves as a friend. Rendered as a 400 problem detail. */
public class OwnFriendCodeException extends ApiException {

    public OwnFriendCodeException() {
        super(HttpStatus.BAD_REQUEST, "That's your own friend code. Send it to friends so they can add you.");
    }
}
