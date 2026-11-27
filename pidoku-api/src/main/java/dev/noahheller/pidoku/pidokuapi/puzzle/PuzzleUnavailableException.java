package dev.noahheller.pidoku.pidokuapi.puzzle;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The request was fine, but the generator job hasn't produced a puzzle for it yet (an empty pool,
 * or no daily for today). Rendered as a 503 problem detail, since it should fix itself once the
 * job runs.
 */
public class PuzzleUnavailableException extends ApiException {

    public PuzzleUnavailableException(String detail) {
        super(HttpStatus.SERVICE_UNAVAILABLE, detail);
    }
}
