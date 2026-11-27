package dev.noahheller.pidoku.pidokuapi.profile;

import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The viewer may see a player's profile but not the puzzle history on it (the player's
 * {@code history_visibility} doesn't allow them). The profile already says so
 * ({@code historyVisible}), so this only reaches a client that asks anyway. Rendered as a 404
 * problem detail.
 */
public class HistoryHiddenException extends ApiException {

    public HistoryHiddenException(long playerId) {
        super(HttpStatus.NOT_FOUND, "Player " + playerId + " doesn't show you their puzzle history.");
    }
}
