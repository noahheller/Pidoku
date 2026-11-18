package dev.noahheller.pidoku.pidokuapi.friend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * The player has no friend with this id. Also used for players who exist but aren't their friend,
 * so ids can't be probed for stats. Rendered as a 404 problem detail.
 */
public class FriendNotFoundException extends ErrorResponseException {

    public FriendNotFoundException(long friendId) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "You have no friend with the id " + friendId + "."),
                null);
    }
}
