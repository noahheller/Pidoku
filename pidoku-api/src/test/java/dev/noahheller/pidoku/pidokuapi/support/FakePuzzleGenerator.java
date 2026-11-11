package dev.noahheller.pidoku.pidokuapi.support;

import dev.noahheller.pidoku.pidokuapi.generation.PuzzleGenerator;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.GeneratedPuzzle;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Returns {@link TestPuzzles} instantly instead of running the engine, and counts calls per
 * difficulty. Can be told to time out, like the real generator does when it finds nothing.
 */
public class FakePuzzleGenerator implements PuzzleGenerator {

    private final Map<Difficulty, AtomicInteger> calls = new ConcurrentHashMap<>();
    private volatile boolean timingOut = false;

    @Override
    public Optional<GeneratedPuzzle> generate(Difficulty difficulty, long seed) {
        calls.computeIfAbsent(difficulty, d -> new AtomicInteger()).incrementAndGet();
        return timingOut ? Optional.empty() : Optional.of(TestPuzzles.generated(difficulty, seed));
    }

    public int calls(Difficulty difficulty) {
        AtomicInteger count = calls.get(difficulty);
        return count == null ? 0 : count.get();
    }

    public int totalCalls() {
        return calls.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    public void setTimingOut(boolean timingOut) {
        this.timingOut = timingOut;
    }

    public void reset() {
        calls.clear();
        timingOut = false;
    }
}
