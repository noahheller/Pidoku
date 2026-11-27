package dev.noahheller.pidoku.pidokuapi.friend;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/** No player has the friend code. Rendered as a 404 problem detail. */
public class FriendCodeNotFoundException extends ApiException {

    public FriendCodeNotFoundException(String code) {
        super(HttpStatus.NOT_FOUND, "No player has the friend code '" + code + "'. Check it and try again.");
    }
}
