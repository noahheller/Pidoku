package dev.noahheller.pidoku.pidokuapi.profile;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An anonymous player asked for someone else's profile: only players signed in with Google may
 * see other players' profiles. Rendered as a 403 problem detail.
 */
public class GoogleSignInRequiredException extends ErrorResponseException {

    public GoogleSignInRequiredException() {
        super(HttpStatus.FORBIDDEN,
                ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Sign in with Google to see other players' profiles."),
                null);
    }
}
