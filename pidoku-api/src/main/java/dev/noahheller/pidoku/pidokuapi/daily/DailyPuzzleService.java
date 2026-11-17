package dev.noahheller.pidoku.pidokuapi.daily;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.GeneratedPuzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleNotFoundException;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleRepository;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleService;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleSource;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleUnavailableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads and assigns daily puzzles: each day has one per difficulty in
 * {@link DailyProperties#difficulties()}. Players ask for their own local date; the injected {@link Clock} (UTC) limits which dates are served.
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

    /** The current UTC date: where the generator job starts filling slots. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Whether {@code date} is "today" for a player somewhere right now. Time zones run from
     * UTC-12 to UTC+14, so that's any date from the one at UTC-12 to the one at UTC+14: yesterday,
     * today or tomorrow in UTC. Stops players from fetching dailies ahead of time.
     */
    public boolean isCurrentSomewhere(LocalDate date) {
        Instant now = clock.instant();
        LocalDate earliest = LocalDate.ofInstant(now, ZoneOffset.ofHours(-12));
        LocalDate latest = LocalDate.ofInstant(now, ZoneOffset.ofHours(14));
        return !date.isBefore(earliest) && !date.isAfter(latest);
    }

    /**
     * One date's dailies, easiest first, with their puzzles loaded. If the generator job filled
     * only some of the date's slots, returns the ones it filled.
     *
     * @throws PuzzleNotFoundException    if the date isn't today anywhere in the world
     * @throws PuzzleUnavailableException if the date has no dailies at all
     */
    @Transactional(readOnly = true)
    public List<DailyPuzzle> forDate(LocalDate date) {
        if (!isCurrentSomewhere(date)) {
            throw new PuzzleNotFoundException("There are no daily puzzles for " + date
                    + ": it isn't that date anywhere right now. Check your device's date.");
        }
        List<DailyPuzzle> found = dailies.findAllWithPuzzle(date).stream()
                .sorted(Comparator.comparing(DailyPuzzle::getDifficulty))
                .toList();
        if (found.isEmpty()) {
            throw new PuzzleUnavailableException("There are no daily puzzles for " + date + " yet.");
        }
        return found;
    }

    /** Whether these dailies cover every daily difficulty, so the date's response won't change again. */
    public boolean isComplete(List<DailyPuzzle> dateDailies) {
        return dateDailies.size() >= difficulties.size();
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
