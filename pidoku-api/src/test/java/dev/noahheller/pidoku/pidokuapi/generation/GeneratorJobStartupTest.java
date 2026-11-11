package dev.noahheller.pidoku.pidokuapi.generation;

import dev.noahheller.pidoku.pidokuapi.daily.DailyPuzzleRepository;
import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.PuzzleService;
import dev.noahheller.pidoku.pidokuapi.support.GeneratorTestConfiguration;
import dev.noahheller.pidoku.pidokuapi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the application the way the Cloud Run Job does (the {@code generator} profile, no web
 * server), so {@link GeneratorJob} runs during startup. Catches beans that only work in a web
 * application. Doesn't extend IntegrationTest, whose table cleanup would run after the job.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "pidoku.generator.pool-size-per-difficulty=1",
        "pidoku.generator.daily-days-ahead=2"
})
@ActiveProfiles(GeneratorJob.PROFILE)
@Import({TestcontainersConfiguration.class, GeneratorTestConfiguration.class})
class GeneratorJobStartupTest {

    @Autowired
    PuzzleService puzzleService;

    @Autowired
    DailyPuzzleRepository dailies;

    @Test
    void jobRanDuringStartup() {
        for (Difficulty difficulty : Difficulty.values()) {
            assertThat(puzzleService.countInPool(difficulty)).as(difficulty.id()).isOne();
        }
        // Two days of easy, medium and hard.
        assertThat(dailies.count()).isEqualTo(6);
    }
}
