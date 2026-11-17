package dev.noahheller.pidoku.pidokuapi.daily;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleSource;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5 is a one-off data fix, so Flyway's run of it in the tests (on empty tables) checks nothing.
 * This runs its SQL again on data like production's.
 */
class RegenerateFutureDailiesMigrationTest extends IntegrationTest {

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    private int seed;

    private Puzzle fill(LocalDate date) {
        return dailyPuzzleService.fillWithNew(new DailySlot(date, Difficulty.EASY),
                TestPuzzles.generated(Difficulty.EASY, seed++)).getPuzzle();
    }

    private String source(Puzzle puzzle) {
        return jdbc.queryForObject("SELECT source FROM puzzles WHERE id = ?", String.class, puzzle.getId());
    }

    @Test
    void clearsDailiesNoOneHasBeenServedAndReturnsTheirPuzzlesToThePool() throws IOException {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Puzzle yesterdays = fill(today.minusDays(1));
        Puzzle todays = fill(today);
        Puzzle tomorrows = fill(today.plusDays(1));
        Puzzle inTwoDays = fill(today.plusDays(2));
        Puzzle inAMonth = fill(today.plusDays(30));

        jdbc.execute(new ClassPathResource("db/migration/V5__regenerate_future_dailies.sql")
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(jdbc.queryForList("SELECT puzzle_date FROM daily_puzzles ORDER BY puzzle_date", LocalDate.class))
                .containsExactly(today.minusDays(1), today, today.plusDays(1));
        assertThat(source(yesterdays)).isEqualTo(PuzzleSource.DAILY.name());
        assertThat(source(todays)).isEqualTo(PuzzleSource.DAILY.name());
        assertThat(source(tomorrows)).isEqualTo(PuzzleSource.DAILY.name());
        assertThat(source(inTwoDays)).isEqualTo(PuzzleSource.POOL.name());
        assertThat(source(inAMonth)).isEqualTo(PuzzleSource.POOL.name());
    }
}
