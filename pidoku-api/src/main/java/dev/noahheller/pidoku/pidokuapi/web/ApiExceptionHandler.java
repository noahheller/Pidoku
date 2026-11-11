package dev.noahheller.pidoku.pidokuapi.web;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns errors into HTTP problem details ({@code application/problem+json}), e.g.
 * {@code {"status": 404, "title": "Not Found", "detail": "No puzzle has the share code 'X'."}}.
 *
 * <p>Spring's base class already handles its own exceptions and any
 * {@link org.springframework.web.ErrorResponseException}, which is what the API's exceptions
 * extend, so most errors need nothing here.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * A request value that couldn't be converted, like {@code ?difficulty=impossible}. Spring's
     * default detail only says the conversion failed; when our converter explained why (for
     * difficulties, by listing the valid ones), pass that on.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleTypeMismatch(ex, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && ex.getMostSpecificCause() instanceof IllegalArgumentException cause
                && cause.getMessage() != null) {
            problem.setDetail(cause.getMessage());
        }
        return response;
    }
}
