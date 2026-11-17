package dev.noahheller.pidoku.pidokuapi.attempt;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** The grid sent to complete an attempt isn't the puzzle's solution. Rendered as a 422 problem detail. */
public class WrongSolutionException extends ErrorResponseException {

    public WrongSolutionException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT,
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                        "That grid isn't the puzzle's solution."),
                null);
    }
}
