package dev.noahheller.pidoku.pidokuapi.generation;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.GeneratedPuzzle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Generates several puzzles of one difficulty in parallel, each from its own random seed.
 *
 * <p>Generation runs on worker threads, but every finished puzzle is handed to the caller's
 * callback on the calling thread, as soon as it's ready. So callers can store puzzles without
 * worrying about threads, and a job that dies halfway keeps what it already stored.
 */
@Slf4j
@Component
public class PuzzleBatchGenerator {

    private final PuzzleGenerator generator;
    private final int parallelism;

    public PuzzleBatchGenerator(PuzzleGenerator generator, GeneratorProperties properties) {
        this.generator = generator;
        this.parallelism = properties.effectiveParallelism();
    }

    /**
     * Tries to generate {@code count} puzzles. Attempts that time out are logged and skipped, not retried.
     *
     * @param onGenerated called on the calling thread with each puzzle, in the order they finish
     * @return how many puzzles were generated (and passed to {@code onGenerated})
     */
    public int generate(Difficulty difficulty, int count, Consumer<GeneratedPuzzle> onGenerated) {
        if (count <= 0) {
            return 0;
        }
        // Daemon threads: generation can't be interrupted, so if the caller fails partway through,
        // leftover attempts mustn't keep the JVM alive after the job exits.
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(parallelism, count),
                Thread.ofPlatform().name("puzzle-generator-", 1).daemon(true).factory());
        try {
            CompletionService<Optional<GeneratedPuzzle>> completion = new ExecutorCompletionService<>(executor);
            for (int i = 0; i < count; i++) {
                long seed = ThreadLocalRandom.current().nextLong();
                completion.submit(() -> generator.generate(difficulty, seed));
            }
            int generated = 0;
            for (int i = 0; i < count; i++) {
                Optional<GeneratedPuzzle> puzzle = awaitNext(completion);
                if (puzzle.isPresent()) {
                    onGenerated.accept(puzzle.get());
                    generated++;
                } else {
                    log.warn("Gave up on a {} puzzle: no puzzle found before the timeout", difficulty.id());
                }
            }
            return generated;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Optional<GeneratedPuzzle> awaitNext(CompletionService<Optional<GeneratedPuzzle>> completion) {
        try {
            return completion.take().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for puzzle generation", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Puzzle generation failed", e.getCause());
        }
    }
}
