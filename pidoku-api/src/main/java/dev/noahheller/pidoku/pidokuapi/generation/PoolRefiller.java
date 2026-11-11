package dev.noahheller.pidoku.pidokuapi.generation;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleService;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tops the random pool up to {@link GeneratorProperties#poolSizePerDifficulty()} puzzles of each
 * difficulty.
 */
@Slf4j
@Component
public class PoolRefiller {

    private final PuzzleService puzzleService;
    private final PuzzleBatchGenerator batchGenerator;
    private final int targetSize;

    public PoolRefiller(PuzzleService puzzleService, PuzzleBatchGenerator batchGenerator,
                        GeneratorProperties properties) {
        this.puzzleService = puzzleService;
        this.batchGenerator = batchGenerator;
        this.targetSize = properties.poolSizePerDifficulty();
    }

    /** @return how many puzzles were added, across all difficulties */
    public int refill() {
        int added = 0;
        for (Difficulty difficulty : Difficulty.values()) {
            added += refill(difficulty);
        }
        return added;
    }

    private int refill(Difficulty difficulty) {
        long inPool = puzzleService.countInPool(difficulty);
        int missing = (int) Math.max(0, targetSize - inPool);
        if (missing == 0) {
            log.info("Pool has {} {} puzzles, no refill needed", inPool, difficulty.id());
            return 0;
        }
        log.info("Pool has {} {} puzzles, generating {} more", inPool, difficulty.id(), missing);
        int added = batchGenerator.generate(difficulty, missing,
                puzzle -> puzzleService.store(puzzle, PuzzleSource.POOL));
        if (added < missing) {
            log.warn("Added only {} of {} {} puzzles", added, missing, difficulty.id());
        }
        return added;
    }
}
