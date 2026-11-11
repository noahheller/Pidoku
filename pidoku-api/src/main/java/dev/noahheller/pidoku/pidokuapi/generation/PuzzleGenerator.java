package dev.noahheller.pidoku.pidokuapi.generation;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.GeneratedPuzzle;

import java.util.Optional;

/**
 * Generates one puzzle. Implementations must be safe to call from several threads at once,
 * because {@link PuzzleBatchGenerator} generates in parallel.
 */
public interface PuzzleGenerator {

    /**
     * @param seed makes generation reproducible: the same difficulty and seed give the same
     *             puzzle (for the same engine version)
     * @return the puzzle, or empty if none was found in time
     */
    Optional<GeneratedPuzzle> generate(Difficulty difficulty, long seed);
}
