package dev.noahheller.pidoku.pidokuapi.generation;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.GeneratedPuzzle;
import dev.noahheller.pidokuengine.PidokuEngine;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses the real engine, on the fastest difficulty. The engine's own tests cover puzzle quality. */
class EnginePuzzleGeneratorTest {

    private final EnginePuzzleGenerator generator =
            new EnginePuzzleGenerator(new GeneratorProperties(0, 1, 1, Duration.ofSeconds(30)));

    @Test
    void generatedPuzzleDescribesItself() {
        GeneratedPuzzle puzzle = generator.generate(Difficulty.TRIVIAL, 7L).orElseThrow();

        assertThat(puzzle.difficulty()).isEqualTo(Difficulty.TRIVIAL);
        assertThat(puzzle.size()).isEqualTo(9);
        assertThat(puzzle.givens()).hasSize(81).matches("[0-9]+");
        assertThat(puzzle.solution()).hasSize(81).matches("[1-9]+");
        assertThat(puzzle.engineVersion()).isEqualTo(PidokuEngine.VERSION);
        assertThat(puzzle.seed()).isEqualTo(7L);
        assertThat(StrategyEnum.valueOf(puzzle.hardestStrategy())).isNotNull();
        assertThat(puzzle.strategyUses().keySet()).allSatisfy(name -> StrategyEnum.valueOf(name));
    }

    @Test
    void givensAgreeWithTheSolution() {
        GeneratedPuzzle puzzle = generator.generate(Difficulty.TRIVIAL, 7L).orElseThrow();

        for (int i = 0; i < puzzle.givens().length(); i++) {
            char given = puzzle.givens().charAt(i);
            if (given != '0') {
                assertThat(given).as("cell %d", i).isEqualTo(puzzle.solution().charAt(i));
            }
        }
    }

    @Test
    void everyEmptyCellIsFilledByExactlyOnePlacement() {
        GeneratedPuzzle puzzle = generator.generate(Difficulty.TRIVIAL, 7L).orElseThrow();

        int placements = puzzle.strategyUses().entrySet().stream()
                .filter(use -> StrategyEnum.valueOf(use.getKey()).placesDigits())
                .mapToInt(use -> use.getValue())
                .sum();
        assertThat(placements).isEqualTo(81 - puzzle.clueCount());
    }

    @Test
    void sameSeedGivesTheSamePuzzle() {
        assertThat(generator.generate(Difficulty.TRIVIAL, 11L))
                .isEqualTo(generator.generate(Difficulty.TRIVIAL, 11L));
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void everyDifficultyUsesTheEngineProfileOfTheSameName(Difficulty difficulty) {
        assertThat(EnginePuzzleGenerator.profileFor(difficulty).name).isEqualToIgnoringCase(difficulty.name());
    }
}
