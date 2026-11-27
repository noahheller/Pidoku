package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.puzzle.Grids;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleNotFoundException;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleRepository;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

/**
 * Starts and completes attempts. Both times come from the server's {@link Clock}, so a solve time
 * is measured here rather than claimed by the client. It's wall-clock time from starting the
 * puzzle to solving it, including any time spent away. The one claim taken from the client is how
 * late a completion was sent (offline, API down), which is subtracted.
 */
@Service
public class AttemptService {

    private final AttemptRepository attempts;
    private final PuzzleRepository puzzles;
    private final Clock clock;

    public AttemptService(AttemptRepository attempts, PuzzleRepository puzzles, Clock clock) {
        this.attempts = attempts;
        this.puzzles = puzzles;
        this.clock = clock;
    }

    /**
     * The player's unfinished attempt at a puzzle, started now if they don't have one. Calling it
     * again (a page reload, another device) returns the same attempt with its original start time.
     *
     * <p>Daily puzzles are ranked, so their clock never restarts: otherwise a player could study
     * the board, restart and solve it fast. Restarting a daily clears the board but keeps the time.
     *
     * @param restart true when the player restarted the puzzle: an unfinished attempt's clock then
     *                starts again from now, matching the timer on their screen (ignored for dailies)
     * @throws PuzzleNotFoundException if no puzzle has the id
     */
    @Transactional
    public Attempt start(Player player, long puzzleId, boolean restart) {
        Puzzle puzzle = puzzles.findById(puzzleId)
                .orElseThrow(() -> new PuzzleNotFoundException("No puzzle has the id " + puzzleId + "."));
        boolean restartClock = restart && puzzle.getSource() != PuzzleSource.DAILY;
        return attempts.findOpen(player, puzzleId)
                .map(open -> {
                    if (restartClock) {
                        open.restart(clock.instant());
                    } else if (restart) {
                        // A daily's board was wiped: its penalties stay, but hints start over.
                        open.forgetHint();
                    }
                    return open;
                })
                .orElseGet(() -> {
                    attempts.insertIfNoneOpen(player.getId(), puzzleId, clock.instant());
                    return attempts.findOpen(player, puzzleId).orElseThrow();
                });
    }

    /**
     * Completes an attempt if {@code grid} is the puzzle's solution, recording the time since it
     * started. Completing an attempt that's already complete changes nothing and returns it, so a
     * retried request is harmless.
     *
     * @param grid     the player's finished grid, flat and row-major
     * @param mistakes wrong digits entered along the way, as counted by the client
     * @param activeMs the client's own timer, paused while the tab was hidden; null if not sent
     * @param lateByMs how long ago the puzzle was solved, for a completion the client couldn't send
     *                 at the time; null if sent straight away. Taken off the recorded time.
     * @param autoNotesUsed the player used Auto notes
     * @throws AttemptNotFoundException if the player has no attempt with the id
     * @throws WrongSolutionException   if the grid isn't the solution
     */
    @Transactional
    public Attempt complete(Player player, long attemptId, int[] grid, int mistakes, Long activeMs, Long lateByMs,
                            boolean autoNotesUsed) {
        Attempt attempt = attempts.findForUpdate(attemptId, player)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
        if (attempt.isCompleted()) {
            return attempt;
        }
        if (!Arrays.equals(grid, Grids.toCells(attempt.getPuzzle().getSolution()))) {
            throw new WrongSolutionException();
        }
        attempt.complete(clock.instant(), mistakes, activeMs, lateByMs == null ? null : Duration.ofMillis(lateByMs),
                autoNotesUsed);
        return attempt;
    }

    /**
     * Saves an unfinished attempt's board, so the player can carry on from it on another device.
     * Saving progress for an attempt that's already complete changes nothing (a save sent as the
     * tab closed can arrive after the completion).
     *
     * @throws AttemptNotFoundException if the player has no attempt with the id
     * @throws InvalidProgressException if the grids don't fit the puzzle
     */
    @Transactional
    public void saveProgress(Player player, long attemptId, AttemptProgress progress, boolean autoNotesUsed) {
        Attempt attempt = attempts.findForUpdate(attemptId, player)
                .orElseThrow(() -> new AttemptNotFoundException(attemptId));
        if (attempt.isCompleted()) {
            return;
        }
        Boards.checkFits(progress.values(), progress.notes(), Grids.toCells(attempt.getPuzzle().getGivens()));
        attempt.saveProgress(progress, autoNotesUsed, clock.instant());
    }
}
