package dev.noahheller.pidoku.pidokuapi.puzzle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DifficultyTest {

    @Test
    void idIsTheLowerCaseName() {
        assertThat(Difficulty.FIENDISH.id()).isEqualTo("fiendish");
    }

    @Test
    void fromIdIgnoresCaseAndSurroundingSpaces() {
        assertThat(Difficulty.fromId("hard")).isEqualTo(Difficulty.HARD);
        assertThat(Difficulty.fromId(" Relaxing ")).isEqualTo(Difficulty.RELAXING);
    }

    @Test
    void fromIdNamesTheValidDifficultiesWhenItFails() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Difficulty.fromId("impossible"))
                .withMessageContaining("'impossible'")
                .withMessageContaining("trivial, relaxing, easy, medium, hard, fiendish");
    }
}
