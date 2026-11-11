package dev.noahheller.pidoku.pidokuapi.daily;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.GeneratedPuzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleRepository;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleService;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleSource;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads and assigns daily puzzles: each day has one per difficulty in
 * {@link DailyProperties#difficulties()}. "Today" is the UTC date of the injected {@link Clock}.
 */
@Service
public class DailyPuzzleService {

    private final DailyPuzzleRepository dailies;
    private final PuzzleRepository puzzles;
    private final PuzzleService puzzleService;
    private final List<Difficulty> difficulties;
    private final Clock clock;

    public DailyPuzzleService(DailyPuzzleRepository dailies, PuzzleRepository puzzles, PuzzleService puzzleService,
                              DailyProperties properties, Clock clock) {
        this.dailies = dailies;
        this.puzzles = puzzles;
        this.puzzleService = puzzleService;
        this.difficulties = properties.difficulties();
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Today's dailies, easiest first, with their puzzles loaded. If the generator job filled only
     * some of today's slots, returns the ones it filled.
     *
     * @throws PuzzleUnavailableException if today has no dailies at all
     */
    @Transactional(readOnly = true)
    public List<DailyPuzzle> forToday() {
        LocalDate today = today();
        List<DailyPuzzle> todays = dailies.findAllWithPuzzle(today).stream()
                .sorted(Comparator.comparing(DailyPuzzle::getDifficulty))
                .toList();
        if (todays.isEmpty()) {
            throw new PuzzleUnavailableException("There are no daily puzzles for " + today + " yet.");
        }
        return todays;
    }

    /**
     * Slots from {@code from} to {@code to} (both inclusive) that don't have a puzzle yet, for every
     * daily difficulty: earliest date first, then easiest difficulty first.
     */
    @Transactional(readOnly = true)
    public List<DailySlot> emptySlots(LocalDate from, LocalDate to) {
        Set<DailySlot> filled = dailies.findFilledSlots(from, to);
        List<DailySlot> empty = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            for (Difficulty difficulty : difficulties) {
                DailySlot slot = new DailySlot(date, difficulty);
                if (!filled.contains(slot)) {
                    empty.add(slot);
                }
            }
        }
        return empty;
    }

    /**
     * Fills a slot with a random pool puzzle of the slot's difficulty.
     *
     * @return the new daily, or empty if the pool has no puzzle of that difficulty
     */
    @Transactional
    public Optional<DailyPuzzle> fillFromPool(DailySlot slot) {
        return puzzles.findRandomForUpdate(PuzzleSource.POOL, slot.difficulty())
                .map(puzzle -> {
                    puzzle.moveFromPoolToDaily();
                    return dailies.save(new DailyPuzzle(slot, puzzle));
                });
    }

    /**
     * Stores a newly generated puzzle and fills a slot with it, in one transaction.
     *
     * @throws IllegalArgumentException if the puzzle's difficulty isn't the slot's (nothing is stored)
     */
    @Transactional
    public DailyPuzzle fillWithNew(DailySlot slot, GeneratedPuzzle generated) {
        Puzzle puzzle = puzzleService.store(generated, PuzzleSource.DAILY);
        return dailies.save(new DailyPuzzle(slot, puzzle));
    }
}
