package dev.noahheller.pidoku.pidokuapi.attempt;

import java.time.Instant;

/**
 * An attempt as the frontend receives it. {@code completedAt} and {@code elapsedMs} are null
 * while it's unfinished.
 */
public record AttemptResponse(
        long id,
        long puzzleId,
        Instant startedAt,
        Instant completedAt,
        Long elapsedMs,
        int mistakes,
        int hintsUsed
) {

    public static AttemptResponse from(Attempt attempt) {
        return new AttemptResponse(
                attempt.getId(),
                attempt.getPuzzle().getId(),
                attempt.getStartedAt(),
                attempt.getCompletedAt(),
                attempt.getElapsedMs(),
                attempt.getMistakes(),
                attempt.getHintsUsed());
    }
}
