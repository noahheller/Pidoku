package dev.noahheller.pidoku.pidokuapi.daily;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DailyPropertiesTest {

    @Test
    void keepsDifficultiesEasiestFirst() {
        DailyProperties properties = new DailyProperties(List.of(Difficulty.HARD, Difficulty.EASY, Difficulty.MEDIUM));

        assertThat(properties.difficulties()).containsExactly(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD);
    }

    @Test
    void refusesADifficultyListedTwice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DailyProperties(List.of(Difficulty.EASY, Difficulty.EASY)))
                .withMessageContaining("twice");
    }

    @Test
    void treatsAMissingListAsEmptySoValidationCanReportIt() {
        assertThat(new DailyProperties(null).difficulties()).isEmpty();
    }
}
