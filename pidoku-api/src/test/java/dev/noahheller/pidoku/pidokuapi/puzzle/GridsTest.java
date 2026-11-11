package dev.noahheller.pidoku.pidokuapi.puzzle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GridsTest {

    @Test
    void encodesRowByRowWithZeroForEmptyCells() {
        int[][] grid = {
                {3, 0, 1, 4},
                {0, 0, 0, 0},
                {1, 4, 3, 0},
                {0, 3, 0, 1}};
        assertThat(Grids.encode(grid)).isEqualTo("3014000014300301");
    }

    @Test
    void toCellsReversesEncode() {
        assertThat(Grids.toCells("3014000014300301"))
                .containsExactly(3, 0, 1, 4, 0, 0, 0, 0, 1, 4, 3, 0, 0, 3, 0, 1);
    }

    @Test
    void countsClues() {
        assertThat(Grids.countClues("3014000014300301")).isEqualTo(8);
    }

    @Test
    void rejectsGridsThatArentSquare() {
        assertThatIllegalArgumentException().isThrownBy(() -> Grids.encode(new int[][]{{1, 2}, {3}}));
    }

    @Test
    void rejectsValuesThatArentOneDigit() {
        assertThatIllegalArgumentException().isThrownBy(() -> Grids.encode(new int[][]{{10}}));
        assertThatIllegalArgumentException().isThrownBy(() -> Grids.encode(new int[][]{{-1}}));
        assertThatIllegalArgumentException().isThrownBy(() -> Grids.toCells("12x4"));
    }
}
