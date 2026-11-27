package dev.noahheller.pidoku.pidokuapi.streak;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;

/** The "today" a client sent isn't the date anywhere in the world. Rendered as a 400 problem detail. */
public class InvalidTodayException extends ApiException {

    public InvalidTodayException(LocalDate today) {
        super(HttpStatus.BAD_REQUEST, "It isn't " + today + " anywhere right now. Check your device's date.");
    }
}
