package dev.noahheller.pidoku.pidokuapi.daily;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleRepository;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleService;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleSource;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DailyPuzzleServiceTest extends IntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 12, 1);
    private static final DailySlot EASY_SLOT = new DailySlot(DATE, Difficulty.EASY);
    private static final DailySlot HARD_SLOT = new DailySlot(DATE, Difficulty.HARD);

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    @Autowired
    PuzzleService puzzleService;

    @Autowired
    PuzzleRepository puzzles;

    @Autowired
    DailyPuzzleRepository dailies;

    @Test
    void fillFromPoolMovesAPuzzleOfTheSlotsDifficultyOutOfThePool() {
        Puzzle hard = puzzleService.store(TestPuzzles.generated(Difficulty.HARD), PuzzleSource.POOL);
        puzzleService.store(TestPuzzles.generated(Difficulty.EASY), PuzzleSource.POOL);

        DailyPuzzle daily = dailyPuzzleService.fillFromPool(HARD_SLOT).orElseThrow();

        assertThat(daily.getPuzzle().getId()).isEqualTo(hard.getId());
        assertThat(puzzles.findById(hard.getId()).orElseThrow().getSource()).isEqualTo(PuzzleSource.DAILY);
        assertThat(puzzleService.countInPool(Difficulty.HARD)).isZero();
        assertThat(puzzleService.countInPool(Difficulty.EASY)).isOne();
    }

    @Test
    void fillFromPoolReturnsEmptyWhenThePoolHasNoneOfThatDifficulty() {
        puzzleService.store(TestPuzzles.generated(Difficulty.EASY), PuzzleSource.POOL);

        assertThat(dailyPuzzleService.fillFromPool(HARD_SLOT)).isEmpty();
        assertThat(dailies.count()).isZero();
    }

    @Test
    void fillWithNewStoresThePuzzleAsADaily() {
        DailyPuzzle daily = dailyPuzzleService.fillWithNew(EASY_SLOT, TestPuzzles.generated(Difficulty.EASY));

        assertThat(puzzles.findById(daily.getPuzzle().getId()).orElseThrow().getSource()).isEqualTo(PuzzleSource.DAILY);
        assertThat(dailies.findById(EASY_SLOT)).isPresent();
    }

    @Test
    void aDateHoldsOneDailyPerDifficulty() {
        dailyPuzzleService.fillWithNew(new DailySlot(DATE, Difficulty.EASY), TestPuzzles.generated(Difficulty.EASY));
        dailyPuzzleService.fillWithNew(new DailySlot(DATE, Difficulty.MEDIUM), TestPuzzles.generated(Difficulty.MEDIUM));
        dailyPuzzleService.fillWithNew(new DailySlot(DATE, Difficulty.HARD), TestPuzzles.generated(Difficulty.HARD));

        assertThat(dailies.findAllWithPuzzle(DATE)).hasSize(3);
    }

    @Test
    void aSlotCanOnlyBeFilledOnce() {
        dailyPuzzleService.fillWithNew(EASY_SLOT, TestPuzzles.generated(Difficulty.EASY));

        assertThatThrownBy(() -> dailyPuzzleService.fillWithNew(EASY_SLOT, TestPuzzles.generated(Difficulty.EASY)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // The failed fill rolled back, so its puzzle wasn't stored either.
        assertThat(puzzles.count()).isOne();
    }

    @Test
    void aPuzzleOnlyFillsASlotOfItsOwnDifficulty() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> dailyPuzzleService.fillWithNew(EASY_SLOT, TestPuzzles.generated(Difficulty.HARD)))
                .withMessageContaining("hard puzzle can't fill the easy daily slot");
        assertThat(puzzles.count()).isZero();
    }

    @Test
    void databaseAlsoRefusesAPuzzleOfTheWrongDifficulty() {
        long hardId = puzzleService.store(TestPuzzles.generated(Difficulty.HARD), PuzzleSource.DAILY).getId();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO daily_puzzles (puzzle_date, difficulty, puzzle_id) VALUES (?, 'EASY', ?)", DATE, hardId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("daily_puzzles_puzzle_fkey");
    }

    @Test
    void emptySlotsListsEveryDailyDifficultyForEachDateThatLacksIt() {
        dailyPuzzleService.fillWithNew(new DailySlot(DATE, Difficulty.MEDIUM), TestPuzzles.generated(Difficulty.MEDIUM));

        // application.properties: easy, medium and hard.
        assertThat(dailyPuzzleService.emptySlots(DATE, DATE.plusDays(1))).containsExactly(
                new DailySlot(DATE, Difficulty.EASY),
                new DailySlot(DATE, Difficulty.HARD),
                new DailySlot(DATE.plusDays(1), Difficulty.EASY),
                new DailySlot(DATE.plusDays(1), Difficulty.MEDIUM),
                new DailySlot(DATE.plusDays(1), Difficulty.HARD));
    }
}
