package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/** The grid sent to complete an attempt isn't the puzzle's solution. Rendered as a 422 problem detail. */
public class WrongSolutionException extends ApiException {

    public WrongSolutionException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "That grid isn't the puzzle's solution.");
    }
}
