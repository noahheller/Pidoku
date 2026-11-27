package dev.noahheller.pidoku.pidokuapi.puzzle;

import dev.noahheller.pidoku.pidokuapi.player.Player;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/** Reads and stores puzzles. */
@Service
public class PuzzleService {

    private final PuzzleRepository puzzles;
    private final ShareCodeGenerator shareCodes;

    public PuzzleService(PuzzleRepository puzzles, ShareCodeGenerator shareCodes) {
        this.puzzles = puzzles;
        this.shareCodes = shareCodes;
    }

    /** @throws PuzzleUnavailableException if the pool has no puzzle of this difficulty */
    @Transactional(readOnly = true)
    public Puzzle randomFromPool(Difficulty difficulty) {
        return puzzles.findRandom(PuzzleSource.POOL, difficulty)
                .orElseThrow(() -> new PuzzleUnavailableException(
                        "There are no " + difficulty.id() + " puzzles available right now."));
    }

    /**
     * A random pool puzzle the player hasn't started yet. Once they've started every puzzle of
     * this difficulty, the one they started longest ago, marked as a repeat: a repeat beats having
     * nothing to play, and the nightly job adds more (see {@code PoolRefiller}).
     *
     * @throws PuzzleUnavailableException if the pool has no puzzle of this difficulty
     */
    @Transactional(readOnly = true)
    public RandomPick randomFromPool(Difficulty difficulty, Player player) {
        return puzzles.findRandomUnseen(PuzzleSource.POOL, difficulty, player.getId())
                .map(puzzle -> new RandomPick(puzzle, false))
                .or(() -> puzzles.findLeastRecentlyStarted(PuzzleSource.POOL, difficulty, player.getId())
                        .map(puzzle -> new RandomPick(puzzle, true)))
                .orElseGet(() -> new RandomPick(randomFromPool(difficulty), false));
    }

    /**
     * @param repeat the player has started every pool puzzle of this difficulty, so this is one
     *               they've played before
     */
    public record RandomPick(Puzzle puzzle, boolean repeat) {
    }

    @Transactional(readOnly = true)
    public long countInPool(Difficulty difficulty) {
        return puzzles.countBySourceAndDifficulty(PuzzleSource.POOL, difficulty);
    }

    /** The most pool puzzles of this difficulty any one player has started. */
    @Transactional(readOnly = true)
    public int maxStartedInPoolByOnePlayer(Difficulty difficulty) {
        return puzzles.maxStartedByOnePlayer(PuzzleSource.POOL, difficulty);
    }

    /**
     * Finds a puzzle by share code, ignoring case and surrounding spaces.
     *
     * @throws PuzzleNotFoundException if no puzzle has the code
     */
    @Transactional(readOnly = true)
    public Puzzle byShareCode(String shareCode) {
        return puzzles.findByShareCode(ShareCodeGenerator.normalize(shareCode))
                .orElseThrow(() -> PuzzleNotFoundException.forShareCode(shareCode));
    }

    /** The date the puzzle is the daily for, if it's a daily. */
    @Transactional(readOnly = true)
    public Optional<LocalDate> dailyDate(Puzzle puzzle) {
        return puzzles.findDailyDate(puzzle.getId());
    }

    /**
     * Stores a generated puzzle under a new share code. Joins the caller's transaction if there
     * is one, so it can be combined with other writes (e.g. assigning a daily).
     */
    @Transactional
    public Puzzle store(GeneratedPuzzle generated, PuzzleSource source) {
        String shareCode = shareCodes.nextUnused("share code", puzzles::existsByShareCode);
        return puzzles.save(new Puzzle(generated, shareCode, source));
    }
}
