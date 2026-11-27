package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.utils.MathUtils;

import java.util.Arrays;

public class PidokuRules {

    public static final int EMPTY = 0;
    public final int MIN_VALUE;
    public final int MAX_VALUE;
    private final int[] uniqueDigits;
    private final int[] digitFrequencies;
    private final int size;
    private final int boxDim;
    private final int boxesPerRow;

    private static final int[] DIGITS_OF_PI = {
            3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5,
            8, 9, 7, 9, 3, 2, 3, 8, 4, 6,
            2, 6, 4, 3, 3, 8, 3, 2, 7, 9,
            5, 0, 2, 8, 8, 4, 1, 9, 7, 1,
            6, 9, 3, 9, 9, 3, 7, 5, 1, 0,
            5, 8, 2, 0, 9, 7, 4, 9, 4, 4,
            5, 9, 2, 3, 0, 7, 8, 1, 6, 4,
            0, 6, 2, 8, 6, 2, 0, 8, 9, 9,
            8, 6, 2, 8, 0, 3, 4, 8, 2, 5,
            3, 4, 2, 1, 1, 7, 0, 6, 7, 9
    };

    public PidokuRules(int size) {
        if (size > DIGITS_OF_PI.length) {
            throw new IllegalArgumentException("size(" + size + ") must be less than or equal to " + DIGITS_OF_PI.length);
        }
        if (!MathUtils.hasPerfectRoot(size)) {
            throw new IllegalArgumentException("size(" + size + ") must be a perfect square");
        }
        this.size = size;
        this.boxDim = (int) Math.sqrt(size);
        this.boxesPerRow = boxDim == 0 ? 0 : size / boxDim;
        int[] digits = Arrays.copyOf(DIGITS_OF_PI, size);
        if (Arrays.stream(digits).anyMatch(d -> d == EMPTY)) {
            // Pi's first 0 is its 33rd digit, so this rejects size 36 and up.
            throw new IllegalArgumentException("size(" + size + ") would include the digit "
                    + EMPTY + ", which is reserved for empty cells");
        }
        uniqueDigits = Arrays.stream(digits).distinct().toArray();

        digitFrequencies = new int[10]; // indexed by digit 0-9
        int maxValue = -1;
        int minValue = 10;
        for (int value : digits) {
            digitFrequencies[value]++;
            maxValue = Math.max(maxValue, value);
            minValue = Math.min(minValue, value);
        }
        MAX_VALUE = maxValue;
        MIN_VALUE = minValue;
    }

    /** The width (and height) of a box: 3 on a 9x9 board. */
    public int boxDim() {
        return boxDim;
    }

    /** How many boxes there are across a row of boxes. */
    public int boxesPerRow() {
        return boxesPerRow;
    }

    public int totalBoxes() {
        return boxesPerRow * boxesPerRow;
    }

    public int boxIndex(int row, int col) {
        return (row / boxDim) * boxesPerRow + (col / boxDim);
    }

    public boolean isPlayableValue(int value) {
        return value >= 0 && value < digitFrequencies.length && digitFrequencies[value] != 0;
    }

    public int getDigitFrequency(int digit) {
        if (!isPlayableValue(digit)) {
            return 0;
        }
        return digitFrequencies[digit];
    }

    public int[] getUniqueDigits() {
        return uniqueDigits.clone();
    }

    public int getSize() {
        return size;
    }
}
