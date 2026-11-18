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
 * V7 is a one-off data fix, so Flyway's run of it in the tests (on empty tables) checks nothing.
 * This runs its SQL again on stored puzzles and dailies. The schema already rejects RELAXING, so it
 * checks the rest of the shift (Easy, Medium and Hard each move up one), which is also what would
 * collide on the daily_puzzles primary key if done in one step.
 */
class ShiftDifficultyNamesMigrationTest extends IntegrationTest {

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    private int seed;

    private Puzzle fill(LocalDate date, Difficulty difficulty) {
        return dailyPuzzleService.fillWithNew(new DailySlot(date, difficulty),
                TestPuzzles.generated(difficulty, seed++)).getPuzzle();
    }

    private String column(String sql, Puzzle puzzle) {
        return jdbc.queryForObject(sql, String.class, puzzle.getId());
    }

    @Test
    void movesEachNameUpOneAndClearsDailiesFromTomorrowThatNoOneHasStarted() throws IOException {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Puzzle easy = fill(today, Difficulty.EASY);
        Puzzle medium = fill(today, Difficulty.MEDIUM);
        Puzzle hard = fill(today, Difficulty.HARD);
        Puzzle tomorrowsStarted = fill(today.plusDays(1), Difficulty.EASY);
        Puzzle tomorrowsUnstarted = fill(today.plusDays(1), Difficulty.MEDIUM);
        Puzzle future = fill(today.plusDays(2), Difficulty.EASY);
        // Someone at UTC+14, where it's already tomorrow, has started one of tomorrow's dailies.
        jdbc.update("INSERT INTO players (firebase_uid) VALUES ('early-bird')");
        jdbc.update("INSERT INTO attempts (player_id, puzzle_id, started_at) SELECT id, ?, now() FROM players",
                tomorrowsStarted.getId());

        jdbc.execute(new ClassPathResource("db/migration/V7__shift_difficulty_names.sql")
                .getContentAsString(StandardCharsets.UTF_8));

        String puzzleDifficulty = "SELECT difficulty FROM puzzles WHERE id = ?";
        assertThat(column(puzzleDifficulty, easy)).isEqualTo("MEDIUM");
        assertThat(column(puzzleDifficulty, medium)).isEqualTo("HARD");
        assertThat(column(puzzleDifficulty, hard)).isEqualTo("EXPERT");
        assertThat(column(puzzleDifficulty, future)).isEqualTo("MEDIUM");

        String dailyDifficulty = "SELECT difficulty FROM daily_puzzles WHERE puzzle_id = ?";
        assertThat(column(dailyDifficulty, easy)).isEqualTo("MEDIUM");
        assertThat(column(dailyDifficulty, medium)).isEqualTo("HARD");
        assertThat(column(dailyDifficulty, hard)).isEqualTo("EXPERT");

        assertThat(jdbc.queryForList("SELECT puzzle_id FROM daily_puzzles", Long.class))
                .containsExactlyInAnyOrder(easy.getId(), medium.getId(), hard.getId(), tomorrowsStarted.getId());
        assertThat(column(dailyDifficulty, tomorrowsStarted)).isEqualTo("MEDIUM");
        String source = "SELECT source FROM puzzles WHERE id = ?";
        assertThat(column(source, tomorrowsStarted)).isEqualTo(PuzzleSource.DAILY.name());
        assertThat(column(source, tomorrowsUnstarted)).isEqualTo(PuzzleSource.POOL.name());
        assertThat(column(source, future)).isEqualTo(PuzzleSource.POOL.name());
    }
}
