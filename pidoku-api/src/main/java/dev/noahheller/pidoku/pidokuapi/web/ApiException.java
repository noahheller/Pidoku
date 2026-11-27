package dev.noahheller.pidoku.pidokuapi.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An error the API answers with on purpose, rendered as a problem detail with this status and
 * detail (see {@link ApiExceptionHandler}). Subclasses name the errors callers can expect.
 */
public class ApiException extends ErrorResponseException {

    public ApiException(HttpStatus status, String detail) {
        this(status, ProblemDetail.forStatusAndDetail(status, detail));
    }

    /** For a problem detail with more than a detail, e.g. extra properties. */
    protected ApiException(HttpStatus status, ProblemDetail problem) {
        super(status, problem, null);
    }
}
