package dev.noahheller.pidokuengine;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PidokuRulesTest {

    private final PidokuRules rules = new PidokuRules(9);

    @Test
    void nineByNineUsesFirstNineDigitsOfPi() {
        int[] unique = rules.getUniqueDigits();
        Arrays.sort(unique);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 9}, unique);
        assertEquals(1, rules.MIN_VALUE);
        assertEquals(9, rules.MAX_VALUE);
    }

    @Test
    void repeatedDigitsHaveFrequencyTwo() {
        assertEquals(2, rules.getDigitFrequency(1));
        assertEquals(2, rules.getDigitFrequency(5));
        for (int d : new int[]{2, 3, 4, 6, 9}) {
            assertEquals(1, rules.getDigitFrequency(d), "digit " + d);
        }
    }

    @Test
    void digitsNotInPiPrefixAreUnplayable() {
        for (int d : new int[]{0, 7, 8}) {
            assertFalse(rules.isPlayableValue(d), "digit " + d);
            assertEquals(0, rules.getDigitFrequency(d));
        }
        assertTrue(rules.isPlayableValue(3));
    }

    @Test
    void boxGeometry() {
        assertEquals(3, rules.getBoxDim());
        assertEquals(0, rules.boxIndex(0, 0));
        assertEquals(2, rules.boxIndex(0, 8));
        assertEquals(4, rules.boxIndex(4, 4));
        assertEquals(8, rules.boxIndex(8, 8));
    }

    @Test
    void rejectsNonSquareSizes() {
        assertThrows(IllegalArgumentException.class, () -> new PidokuRules(8));
    }
}
