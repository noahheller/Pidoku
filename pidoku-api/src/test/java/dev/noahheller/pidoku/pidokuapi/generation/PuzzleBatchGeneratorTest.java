package dev.noahheller.pidoku.pidokuapi.generation;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.GeneratedPuzzle;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class PuzzleBatchGeneratorTest {

    private static PuzzleBatchGenerator batch(PuzzleGenerator generator) {
        return new PuzzleBatchGenerator(generator, new GeneratorProperties(0, 1, 4, Duration.ofSeconds(1)));
    }

    @Test
    void generatesTheRequestedNumberOfPuzzles() {
        List<GeneratedPuzzle> received = new ArrayList<>();

        int generated = batch((d, seed) -> Optional.of(TestPuzzles.generated(d, seed)))
                .generate(Difficulty.EASY, 10, received::add);

        assertThat(generated).isEqualTo(10);
        assertThat(received).hasSize(10).allSatisfy(p -> assertThat(p.difficulty()).isEqualTo(Difficulty.EASY));
    }

    @Test
    void generatesOnOtherThreadsButDeliversOnTheCallingThread() {
        Set<Thread> generatingThreads = ConcurrentHashMap.newKeySet();
        Set<Thread> deliveringThreads = ConcurrentHashMap.newKeySet();

        batch((d, seed) -> {
            generatingThreads.add(Thread.currentThread());
            return Optional.of(TestPuzzles.generated(d, seed));
        }).generate(Difficulty.EASY, 8, p -> deliveringThreads.add(Thread.currentThread()));

        assertThat(generatingThreads).doesNotContain(Thread.currentThread());
        assertThat(deliveringThreads).containsExactly(Thread.currentThread());
    }

    @Test
    void everyAttemptGetsItsOwnSeed() {
        Set<Long> seeds = ConcurrentHashMap.newKeySet();

        batch((d, seed) -> {
            seeds.add(seed);
            return Optional.of(TestPuzzles.generated(d, seed));
        }).generate(Difficulty.EASY, 50, p -> { });

        assertThat(seeds).hasSize(50);
    }

    @Test
    void skipsAttemptsThatTimeOut() {
        AtomicInteger attempt = new AtomicInteger();
        List<GeneratedPuzzle> received = new ArrayList<>();

        int generated = batch((d, seed) -> attempt.incrementAndGet() % 2 == 0
                ? Optional.of(TestPuzzles.generated(d, seed))
                : Optional.empty())
                .generate(Difficulty.EASY, 6, received::add);

        assertThat(generated).isEqualTo(3);
        assertThat(received).hasSize(3);
    }

    @Test
    void reportsAGeneratorFailure() {
        assertThatIllegalStateException()
                .isThrownBy(() -> batch((d, seed) -> {
                    throw new ArithmeticException("boom");
                }).generate(Difficulty.EASY, 3, p -> { }))
                .withCauseInstanceOf(ArithmeticException.class);
    }

    @Test
    void nothingToDoForZero() {
        AtomicInteger calls = new AtomicInteger();

        int generated = batch((d, seed) -> {
            calls.incrementAndGet();
            return Optional.empty();
        }).generate(Difficulty.EASY, 0, p -> { });

        assertThat(generated).isZero();
        assertThat(calls).hasValue(0);
    }
}
