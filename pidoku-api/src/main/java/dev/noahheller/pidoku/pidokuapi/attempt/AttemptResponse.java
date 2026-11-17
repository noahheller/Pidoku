package dev.noahheller.pidoku.pidokuapi.attempt;

import java.time.Instant;

/**
 * An attempt as the frontend receives it. {@code completedAt}, {@code elapsedMs} and
 * {@code activeMs} are null while it's unfinished.
 *
 * @param elapsedMs the server-measured solve time, which leaderboards rank
 * @param late      the completion was sent some time after the solve (see {@code Attempt#complete})
 */
public record AttemptResponse(
        long id,
        long puzzleId,
        Instant startedAt,
        Instant completedAt,
        Long elapsedMs,
        int mistakes,
        int hintsUsed,
        Long activeMs,
        boolean late
) {

    public static AttemptResponse from(Attempt attempt) {
        return new AttemptResponse(
                attempt.getId(),
                attempt.getPuzzle().getId(),
                attempt.getStartedAt(),
                attempt.getCompletedAt(),
                attempt.getElapsedMs(),
                attempt.getMistakes(),
                attempt.getHintsUsed(),
                attempt.getActiveMs(),
                attempt.isLate());
    }
}
