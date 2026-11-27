package dev.noahheller.pidoku.pidokuapi.friend;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The player has no friend with this id. Also used for players who exist but aren't their friend,
 * so ids can't be probed for stats. Rendered as a 404 problem detail.
 */
public class FriendNotFoundException extends ApiException {

    public FriendNotFoundException(long friendId) {
        super(HttpStatus.NOT_FOUND, "You have no friend with the id " + friendId + ".");
    }
}
