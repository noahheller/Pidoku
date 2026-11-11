package dev.noahheller.pidoku.pidokuapi.generation;

import dev.noahheller.pidoku.pidokuapi.daily.DailyProperties;
import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzle;
import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleRepository;
import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleService;
import dev.noahheller.pidoku.pidokuapi.daily.DailySlot;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleService;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleSource;
import dev.noahheller.pidoku.pidokuapi.support.FakePuzzleGenerator;
import dev.noahheller.pidoku.pidokuapi.support.GeneratorTestConfiguration;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static dev.noahheller.pidoku.pidokuapi.support.GeneratorTestConfiguration.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generator job's steps against a real database, with a {@link FakePuzzleGenerator} in place
 * of the engine and "today" fixed. The dailies are easy, medium and hard (application.properties).
 */
@Import(GeneratorTestConfiguration.class)
@TestPropertySource(properties = {
        "pidoku.generator.pool-size-per-difficulty=" + GeneratorJobTest.POOL_SIZE,
        "pidoku.generator.daily-days-ahead=" + GeneratorJobTest.DAYS_AHEAD,
        "pidoku.generator.parallelism=2"
})
class GeneratorJobTest extends IntegrationTest {

    // A full pool holds enough of each daily difficulty to fill every upcoming slot.
    static final int POOL_SIZE = 3;
    static final int DAYS_AHEAD = 2;
    private static final int DIFFICULTIES = Difficulty.values().length;
    private static final List<Difficulty> DAILY_DIFFICULTIES = List.of(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD);
    private static final int DAILY_SLOTS = DAYS_AHEAD * DAILY_DIFFICULTIES.size();

    @Autowired
    FakePuzzleGenerator generator;

    @Autowired
    PoolRefiller poolRefiller;

    @Autowired
    DailyPuzzleScheduler dailyPuzzleScheduler;

    @Autowired
    PuzzleService puzzleService;

    @Autowired
    DailyPuzzleService dailyPuzzleService;

    @Autowired
    DailyPuzzleRepository dailies;

    @Autowired
    DailyProperties dailyProperties;

    @BeforeEach
    void resetGenerator() {
        generator.reset();
    }

    @Test
    void dailyDifficultiesComeFromApplicationProperties() {
        assertThat(dailyProperties.difficulties()).isEqualTo(DAILY_DIFFICULTIES);
    }

    @Nested
    class Refill {

        @Test
        void fillsEveryDifficultyToTheTargetSize() {
            int added = poolRefiller.refill();

            assertThat(added).isEqualTo(POOL_SIZE * DIFFICULTIES);
            for (Difficulty difficulty : Difficulty.values()) {
                assertThat(puzzleService.countInPool(difficulty)).as(difficulty.id()).isEqualTo(POOL_SIZE);
            }
        }

        @Test
        void onlyGeneratesWhatIsMissing() {
            puzzleService.store(TestPuzzles.generated(Difficulty.EASY), PuzzleSource.POOL);
            for (int i = 0; i < POOL_SIZE; i++) {
                puzzleService.store(TestPuzzles.generated(Difficulty.HARD), PuzzleSource.POOL);
            }

            poolRefiller.refill();

            assertThat(generator.calls(Difficulty.EASY)).isEqualTo(POOL_SIZE - 1);
            assertThat(generator.calls(Difficulty.HARD)).isZero();
            assertThat(generator.calls(Difficulty.MEDIUM)).isEqualTo(POOL_SIZE);
        }

        @Test
        void doesNothingWhenThePoolIsFull() {
            poolRefiller.refill();
            generator.reset();

            assertThat(poolRefiller.refill()).isZero();
            assertThat(generator.totalCalls()).isZero();
        }

        @Test
        void puzzlesUsedElsewhereDontCountTowardThePool() {
            puzzleService.store(TestPuzzles.generated(Difficulty.EASY), PuzzleSource.DAILY);

            poolRefiller.refill();

            assertThat(generator.calls(Difficulty.EASY)).isEqualTo(POOL_SIZE);
        }

        @Test
        void survivesTimeouts() {
            generator.setTimingOut(true);

            assertThat(poolRefiller.refill()).isZero();
            assertThat(generator.totalCalls()).isEqualTo(POOL_SIZE * DIFFICULTIES);
        }
    }

    @Nested
    class Dailies {

        @Test
        void takesDailiesFromThePoolWhenItCan() {
            poolRefiller.refill();
            generator.reset();

            assertThat(dailyPuzzleScheduler.scheduleUpcoming()).isEqualTo(DAILY_SLOTS);

            assertThat(generator.totalCalls()).isZero();
            assertThat(dailies.count()).isEqualTo(DAILY_SLOTS);
            for (Difficulty difficulty : DAILY_DIFFICULTIES) {
                assertThat(puzzleService.countInPool(difficulty)).as(difficulty.id()).isEqualTo(POOL_SIZE - DAYS_AHEAD);
            }
        }

        @Test
        void generatesDailiesWhenThePoolIsEmpty() {
            assertThat(dailyPuzzleScheduler.scheduleUpcoming()).isEqualTo(DAILY_SLOTS);

            for (Difficulty difficulty : Difficulty.values()) {
                int expected = DAILY_DIFFICULTIES.contains(difficulty) ? DAYS_AHEAD : 0;
                assertThat(generator.calls(difficulty)).as(difficulty.id()).isEqualTo(expected);
            }
            assertThat(puzzleService.countInPool(Difficulty.EASY)).isZero();
        }

        @Test
        void givesEachDayAnEasyMediumAndHardPuzzle() {
            dailyPuzzleScheduler.scheduleUpcoming();

            for (LocalDate date = TODAY; date.isBefore(TODAY.plusDays(DAYS_AHEAD)); date = date.plusDays(1)) {
                List<DailyPuzzle> days = dailies.findAllWithPuzzle(date);
                assertThat(days).as(date.toString())
                        .extracting(DailyPuzzle::getDifficulty)
                        .containsExactlyInAnyOrderElementsOf(DAILY_DIFFICULTIES);
                assertThat(days).allSatisfy(daily ->
                        assertThat(daily.getPuzzle().getDifficulty()).isEqualTo(daily.getDifficulty()));
            }
        }

        @Test
        void coversExactlyTodayThroughDaysAhead() {
            dailyPuzzleScheduler.scheduleUpcoming();

            assertThat(dailyPuzzleService.emptySlots(TODAY, TODAY.plusDays(DAYS_AHEAD - 1))).isEmpty();
            assertThat(dailies.findAllWithPuzzle(TODAY.minusDays(1))).isEmpty();
            assertThat(dailies.findAllWithPuzzle(TODAY.plusDays(DAYS_AHEAD))).isEmpty();
        }

        @Test
        void neverReplacesAFilledSlot() {
            DailySlot todaysEasy = new DailySlot(TODAY, Difficulty.EASY);
            long todaysEasyPuzzle = dailyPuzzleService
                    .fillWithNew(todaysEasy, TestPuzzles.generated(Difficulty.EASY)).getPuzzle().getId();

            assertThat(dailyPuzzleScheduler.scheduleUpcoming()).isEqualTo(DAILY_SLOTS - 1);

            assertThat(dailies.findAllWithPuzzle(TODAY))
                    .filteredOn(daily -> daily.getDifficulty() == Difficulty.EASY)
                    .singleElement()
                    .extracting(daily -> daily.getPuzzle().getId())
                    .isEqualTo(todaysEasyPuzzle);
        }

        @Test
        void secondRunHasNothingToDo() {
            dailyPuzzleScheduler.scheduleUpcoming();

            assertThat(dailyPuzzleScheduler.scheduleUpcoming()).isZero();
        }

        @Test
        void leavesSlotsForTheNextRunWhenGenerationTimesOut() {
            generator.setTimingOut(true);

            assertThat(dailyPuzzleScheduler.scheduleUpcoming()).isZero();
            assertThat(dailies.count()).isZero();
        }
    }

    @Test
    void jobLeavesAFullPoolAndEveryUpcomingSlotFilled() {
        new GeneratorJob(poolRefiller, dailyPuzzleScheduler).run(null);

        for (Difficulty difficulty : Difficulty.values()) {
            assertThat(puzzleService.countInPool(difficulty)).as(difficulty.id()).isEqualTo(POOL_SIZE);
        }
        assertThat(dailies.count()).isEqualTo(DAILY_SLOTS);
        // The dailies came from the first refill, then the second refill replaced them.
        assertThat(generator.totalCalls()).isEqualTo(POOL_SIZE * DIFFICULTIES + DAILY_SLOTS);
    }
}
