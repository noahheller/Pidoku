package dev.noahheller.pidoku.pidokuapi.generation;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Makes sure every date from today through {@link GeneratorProperties#dailyDaysAhead()} days has
 * its daily puzzles: one per daily difficulty (see {@code DailyProperties}). Filled slots are
 * never changed.
 *
 * <p>Every daily is a newly generated puzzle, never one from the random pool: a pool puzzle may
 * already have been played (or solved) by someone through the Random buttons, which would spoil it
 * as a daily and put an old attempt on its leaderboard. Once the next 31 days are filled, a normal
 * night generates three puzzles, one per daily difficulty.
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
        // Per difficulty, earliest date first, so if generation falls short the nearest dates are filled.
        Map<Difficulty, List<DailySlot>> byDifficulty = empty.stream()
                .collect(Collectors.groupingBy(DailySlot::difficulty, LinkedHashMap::new, Collectors.toList()));
        int filled = 0;
        for (var entry : byDifficulty.entrySet()) {
            Iterator<DailySlot> slots = entry.getValue().iterator();
            // Generated in parallel; each puzzle is stored as soon as it's ready, so a job that dies
            // partway keeps the dailies it already made.
            int made = batchGenerator.generate(entry.getKey(), entry.getValue().size(),
                    puzzle -> dailyPuzzleService.fillWithNew(slots.next(), puzzle));
            if (made < entry.getValue().size()) {
                log.warn("Generated only {} of {} {} dailies; the next run will try again",
                        made, entry.getValue().size(), entry.getKey().id());
            }
            filled += made;
        }
        log.info("Filled {} of the {} empty daily slots between {} and {}", filled, empty.size(), today, last);
        return filled;
    }
}
