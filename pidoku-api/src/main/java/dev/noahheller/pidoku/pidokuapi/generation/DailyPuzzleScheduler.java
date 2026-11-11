package dev.noahheller.pidoku.pidokuapi.generation;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Makes sure every date from today through {@link GeneratorProperties#dailyDaysAhead()} days has
 * its daily puzzles: one per daily difficulty (see {@code DailyProperties}). Filled slots are
 * never changed.
 *
 * <p>Each slot is filled from the random pool when the pool has a puzzle of that difficulty, and
 * with a puzzle generated on the spot otherwise.
 */
@Slf4j
@Component
public class DailyPuzzleScheduler {

    private final DailyPuzzleService dailyPuzzleService;
    private final PuzzleBatchGenerator batchGenerator;
    private final int daysAhead;

    public DailyPuzzleScheduler(DailyPuzzleService dailyPuzzleService, PuzzleBatchGenerator batchGenerator,
                                GeneratorProperties properties) {
        this.dailyPuzzleService = dailyPuzzleService;
        this.batchGenerator = batchGenerator;
        this.daysAhead = properties.dailyDaysAhead();
    }

    /** @return how many daily slots were filled */
    public int scheduleUpcoming() {
        LocalDate today = dailyPuzzleService.today();
        LocalDate last = today.plusDays(daysAhead - 1L);
        List<DailySlot> empty = dailyPuzzleService.emptySlots(today, last);
        int filled = 0;
        for (DailySlot slot : empty) {
            if (fill(slot)) {
                filled++;
            } else {
                log.warn("Could not find a puzzle for the {} {} daily; the next run will try again",
                        slot.date(), slot.difficulty().id());
            }
        }
        log.info("Filled {} of the {} empty daily slots between {} and {}", filled, empty.size(), today, last);
        return filled;
    }

    private boolean fill(DailySlot slot) {
        if (dailyPuzzleService.fillFromPool(slot).isPresent()) {
            return true;
        }
        log.info("Pool has no {} puzzle for the {} daily, generating one", slot.difficulty().id(), slot.date());
        return batchGenerator.generate(slot.difficulty(), 1,
                puzzle -> dailyPuzzleService.fillWithNew(slot, puzzle)) == 1;
    }
}
