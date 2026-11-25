package dev.noahheller.pidoku.pidokuapi.attempt;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.List;

/**
 * A hint was asked for on a board with wrong digits: reasoning from them would give a wrong hint.
 * Rendered as a 422 problem detail with a {@code wrongCells} property (row-major indices).
 */
public class WrongDigitsException extends ErrorResponseException {

    public WrongDigitsException(List<Integer> wrongCells) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, problem(wrongCells), null);
    }

    private static ProblemDetail problem(List<Integer> wrongCells) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "Fix the wrong digits before asking for a hint.");
        problem.setProperty("wrongCells", wrongCells);
        return problem;
    }
}
