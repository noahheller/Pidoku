package dev.noahheller.pidoku.pidokuapi.profile;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * No player with this id, or one whose profile the viewer may not see. The two look the same, so
 * ids can't be probed for who exists. Rendered as a 404 problem detail.
 */
public class ProfileNotFoundException extends ApiException {

    public ProfileNotFoundException(long playerId) {
        super(HttpStatus.NOT_FOUND, "There's no profile you can see for player " + playerId + ". It may be private.");
    }
}
