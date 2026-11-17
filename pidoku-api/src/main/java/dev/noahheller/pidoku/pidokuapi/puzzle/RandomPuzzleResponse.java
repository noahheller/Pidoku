package dev.noahheller.pidoku.pidokuapi.puzzle;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * A puzzle from the random endpoint: the usual puzzle fields, plus {@code repeat}.
 *
 * @param repeat the signed-in player has started every pool puzzle of this difficulty, so this is
 *               one they've played before (the one they started longest ago). Always false
 *               signed out, when the server can't know.
 */
public record RandomPuzzleResponse(@JsonUnwrapped PuzzleResponse puzzle, boolean repeat) {
}
