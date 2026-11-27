package dev.noahheller.pidoku.pidokuapi.profile;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * An anonymous player asked for someone else's profile: only players signed in with Google may
 * see other players' profiles. Rendered as a 403 problem detail.
 */
public class GoogleSignInRequiredException extends ApiException {

    public GoogleSignInRequiredException() {
        super(HttpStatus.FORBIDDEN, "Sign in with Google to see other players' profiles.");
    }
}
