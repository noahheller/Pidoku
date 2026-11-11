package dev.noahheller.pidoku.pidokuapi.puzzle;

import java.util.Map;
import java.util.Objects;

/**
 * A freshly generated puzzle and its difficulty analysis, before it is stored.
 *
 * @param givens          encoded with {@link Grids#encode}
 * @param solution        encoded with {@link Grids#encode}
 * @param hardestStrategy name of the hardest strategy the human solver needed
 * @param strategyUses    strategy name → times the human solver used it
 * @param engineVersion   the engine version that generated and scored the puzzle
 * @param seed            the seed the puzzle was generated from
 */
public record GeneratedPuzzle(
        Difficulty difficulty,
        int size,
        String givens,
        String solution,
        int score,
        double dependency,
        double maxStepCost,
        int bottleneckSteps,
        String hardestStrategy,
        Map<String, Integer> strategyUses,
        String engineVersion,
        long seed
) {

    public GeneratedPuzzle {
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(givens, "givens");
        Objects.requireNonNull(solution, "solution");
        Objects.requireNonNull(hardestStrategy, "hardestStrategy");
        Objects.requireNonNull(engineVersion, "engineVersion");
        int cells = size * size;
        if (givens.length() != cells || solution.length() != cells) {
            throw new IllegalArgumentException("a size " + size + " puzzle needs " + cells
                    + " cells, but givens has " + givens.length() + " and solution has " + solution.length());
        }
        strategyUses = Map.copyOf(strategyUses);
    }

    public int clueCount() {
        return Grids.countClues(givens);
    }
}
