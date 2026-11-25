package dev.noahheller.pidoku.pidokuapi.attempt;

import java.time.Instant;

/**
 * An attempt as the frontend receives it. {@code completedAt}, {@code elapsedMs} and
 * {@code activeMs} are null while it's unfinished.
 *
 * @param elapsedMs the server-measured wall-clock solve time
 * @param activeMs  the game's own timer, which leaderboards rank (falling back to elapsedMs)
 * @param late      the completion was sent some time after the solve (see {@code Attempt#complete})
 * @param progress  the board as last saved from any device, or null if none was; with
 *                  {@code progressUpdatedAt}, when the server got it, a device can tell whether
 *                  its own copy is newer
 * @param autoNotesUsed the player used Auto notes during the attempt
 * @param hintPenaltyMs time added for hints so far
 * @param penaltyMs     what the leaderboards add to the time: 5 s x recorded mistakes² plus
 *                      {@code hintPenaltyMs} (mistakes are only recorded on completion)
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
        boolean late,
        AttemptProgress progress,
        Instant progressUpdatedAt,
        boolean autoNotesUsed,
        long hintPenaltyMs,
        long penaltyMs
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
                attempt.isLate(),
                attempt.getProgress(),
                attempt.getProgressUpdatedAt(),
                attempt.isAutoNotesUsed(),
                attempt.getHintPenaltyMs(),
                attempt.getPenaltyMs());
    }
}
