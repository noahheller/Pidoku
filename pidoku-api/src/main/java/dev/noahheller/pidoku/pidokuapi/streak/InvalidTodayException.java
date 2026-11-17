package dev.noahheller.pidoku.pidokuapi.streak;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.time.LocalDate;

/** The "today" a client sent isn't the date anywhere in the world. Rendered as a 400 problem detail. */
public class InvalidTodayException extends ErrorResponseException {

    public InvalidTodayException(LocalDate today) {
        super(HttpStatus.BAD_REQUEST,
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                        "It isn't " + today + " anywhere right now. Check your device's date."),
                null);
    }
}
