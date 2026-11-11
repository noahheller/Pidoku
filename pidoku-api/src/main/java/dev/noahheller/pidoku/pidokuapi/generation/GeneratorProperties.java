package dev.noahheller.pidoku.pidokuapi.generation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Settings under {@code pidoku.generator}, used by the generator job.
 *
 * @param poolSizePerDifficulty how many unplayed puzzles of each difficulty the random pool should hold
 * @param dailyDaysAhead        how many days of daily puzzles to have assigned, counting today
 * @param parallelism           puzzles generated at once; 0 means one per available processor
 * @param timeout               how long one puzzle may take before generation gives up on it
 */
@Validated
@ConfigurationProperties("pidoku.generator")
public record GeneratorProperties(
        @Min(0) int poolSizePerDifficulty,
        @Min(1) int dailyDaysAhead,
        @Min(0) int parallelism,
        @NotNull Duration timeout
) {

    /** {@link #parallelism}, with 0 resolved to the number of available processors. */
    public int effectiveParallelism() {
        return parallelism > 0 ? parallelism : Runtime.getRuntime().availableProcessors();
    }
}
