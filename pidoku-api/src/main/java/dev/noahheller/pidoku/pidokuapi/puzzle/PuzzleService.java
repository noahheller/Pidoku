package dev.noahheller.pidoku.pidokuapi.puzzle;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and stores puzzles. */
@Service
public class PuzzleService {

    /**
     * Tries at drawing an unused share code. With a few hundred puzzles stored, a code clashes less
     * than once in a billion draws, so running out means something is broken.
     */
    private static final int MAX_SHARE_CODE_ATTEMPTS = 5;

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

    @Transactional(readOnly = true)
    public long countInPool(Difficulty difficulty) {
        return puzzles.countBySourceAndDifficulty(PuzzleSource.POOL, difficulty);
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

    /**
     * Stores a generated puzzle under a new share code. Joins the caller's transaction if there
     * is one, so it can be combined with other writes (e.g. assigning a daily).
     */
    @Transactional
    public Puzzle store(GeneratedPuzzle generated, PuzzleSource source) {
        return puzzles.save(new Puzzle(generated, unusedShareCode(), source));
    }

    // Checking first keeps the common case free of constraint violations. Two concurrent stores
    // drawing the same unused code would still be caught by the unique constraint.
    private String unusedShareCode() {
        for (int attempt = 0; attempt < MAX_SHARE_CODE_ATTEMPTS; attempt++) {
            String code = shareCodes.next();
            if (!puzzles.existsByShareCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No unused share code found in " + MAX_SHARE_CODE_ATTEMPTS + " attempts");
    }
}
