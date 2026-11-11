package dev.noahheller.pidoku.pidokuapi.generation;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.GeneratedPuzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.Grids;
import dev.noahheller.pidokuengine.DifficultyProfile;
import dev.noahheller.pidokuengine.PidokuBuilder;
import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SolveTrace;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Generates puzzles with pidoku-engine. This is the only class in the API that talks to the engine's
 * generator.
 *
 * <p>Thread-safe: the engine's {@link PidokuBuilder} keeps no state between calls and takes all
 * its randomness from the seed, so one builder is shared by every thread.
 */
@Component
public class EnginePuzzleGenerator implements PuzzleGenerator {

    static final int BOARD_SIZE = 9;

    private final PidokuBuilder builder = PidokuBuilder.standard(new PidokuRules(BOARD_SIZE));
    private final Duration timeout;

    public EnginePuzzleGenerator(GeneratorProperties properties) {
        this.timeout = properties.timeout();
    }

    @Override
    public Optional<GeneratedPuzzle> generate(Difficulty difficulty, long seed) {
        PidokuPuzzle puzzle = builder.generate(profileFor(difficulty), seed, timeout);
        if (puzzle == null) {
            return Optional.empty();
        }
        SolveTrace trace = puzzle.getSolveTrace();
        return Optional.of(new GeneratedPuzzle(
                difficulty,
                BOARD_SIZE,
                Grids.encode(puzzle.getPuzzle()),
                Grids.encode(puzzle.getSolution()),
                trace.getTotalScore(),
                trace.getDependency(),
                trace.getMaxStepCost(),
                trace.getBottleneckSteps(),
                trace.getHardestStrategy().name(),
                strategyNames(trace.getAllUseCounts()),
                puzzle.getEngineVersion(),
                seed));
    }

    /** The engine profile behind each difficulty. A new {@link Difficulty} fails to compile here until mapped. */
    static DifficultyProfile profileFor(Difficulty difficulty) {
        return switch (difficulty) {
            case TRIVIAL -> DifficultyProfile.trivial();
            case RELAXING -> DifficultyProfile.relaxing();
            case EASY -> DifficultyProfile.easy();
            case MEDIUM -> DifficultyProfile.medium();
            case HARD -> DifficultyProfile.hard();
            case FIENDISH -> DifficultyProfile.fiendish();
        };
    }

    private static Map<String, Integer> strategyNames(Map<StrategyEnum, Integer> useCounts) {
        Map<String, Integer> byName = new TreeMap<>();
        useCounts.forEach((strategy, count) -> byName.put(strategy.name(), count));
        return byName;
    }
}
