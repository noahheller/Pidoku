package dev.noahheller.pidoku.pidokuapi.generation;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleService;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tops up the random pool so that, for each difficulty, even the player who has started the most
 * of its puzzles still has {@link GeneratorProperties#poolSizePerDifficulty()} they haven't. The
 * pool grows as people play, rather than staying one fixed set they work through; with nobody
 * playing, it holds exactly that many. Puzzles are never removed: attempts and share links point
 * at them, and the random endpoint skips the ones a player has started.
 *
 * <p>A run never adds more than that many of a difficulty (the most active player can't have
 * started more puzzles than the pool holds), so the job's running time stays bounded.
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
        int mostStarted = puzzleService.maxStartedInPoolByOnePlayer(difficulty);
        long unseenByMostActive = inPool - mostStarted;
        int missing = (int) Math.max(0, targetSize - unseenByMostActive);
        if (missing == 0) {
            log.info("Pool has {} {} puzzles, {} unstarted by its most active player; no refill needed",
                    inPool, difficulty.id(), unseenByMostActive);
            return 0;
        }
        log.info("Pool has {} {} puzzles, {} unstarted by its most active player; generating {} more",
                inPool, difficulty.id(), unseenByMostActive, missing);
        int added = batchGenerator.generate(difficulty, missing,
                puzzle -> puzzleService.store(puzzle, PuzzleSource.POOL));
        if (added < missing) {
            log.warn("Added only {} of {} {} puzzles", added, missing, difficulty.id());
        }
        return added;
    }
}
