package dev.noahheller.pidoku.pidokuapi.player;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisplayNameTest {

    @Test
    void trimsAndCollapsesWhitespace() {
        assertThat(DisplayName.of(" \tAda \n Lovelace  ").value()).isEqualTo("Ada Lovelace");
    }

    @Test
    void countsCharactersNotUtf16Units() {
        // Each emoji is two UTF-16 units but one character to Postgres.
        assertThat(DisplayName.of("🥧".repeat(40)).value()).hasSize(80);
        assertThatThrownBy(() -> DisplayName.of("🥧".repeat(41))).isInstanceOf(InvalidDisplayNameException.class);
    }

    @Test
    void keepsEmojiBuiltWithZeroWidthJoiners() {
        assertThat(DisplayName.of("👩‍💻 Ada").value()).isEqualTo("👩‍💻 Ada");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "a\u0000b", "a​b", "‮evil"})
    void refusesEmptyAndInvisibleNames(String name) {
        assertThatThrownBy(() -> DisplayName.of(name)).isInstanceOf(InvalidDisplayNameException.class);
    }

    @Test
    void refusesNull() {
        assertThatThrownBy(() -> DisplayName.of(null)).isInstanceOf(InvalidDisplayNameException.class);
    }
}
