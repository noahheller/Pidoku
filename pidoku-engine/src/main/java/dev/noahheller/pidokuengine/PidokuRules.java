package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.utils.MathUtils;

import java.util.Arrays;

public class PidokuRules {

    public static final int EMPTY = 0;
    public final int MIN_VALUE;
    private final int[] DIGITS;
    private final int[] UNIQUE_DIGITS;
    private final int[] DIGIT_FREQUENCIES;
    public final int MAX_VALUE;
    private final int size;

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
        if (size < 0) {
            throw new IllegalArgumentException("size(" + size + ") must be > 0");
        }
        this.size = size;
        DIGITS = new int[size];
        DIGIT_FREQUENCIES = new int[size + 1];
        System.arraycopy(DIGITS_OF_PI, 0, DIGITS, 0, size);
        UNIQUE_DIGITS = Arrays.stream(DIGITS).distinct().toArray();

        int maxValue = -1;
        int minValue = 10;
        for (int i = 0; i < size; i++) {
            int value = DIGITS[i];
            DIGIT_FREQUENCIES[value]++;
            if (value > maxValue) {
                maxValue = value;
            }
            if (value < minValue) {
                minValue = value;
            }
        }
        MAX_VALUE = maxValue;
        MIN_VALUE = minValue;
    }

    public int boxDim(int boardSize) {
        return (int) Math.sqrt(boardSize);
    }

    public int boxesPerRow(int boardSize) {
        return boardSize / boxDim(boardSize);
    }

    public int totalBoxes(int boardSize) {
        int boxesPerRow = boxesPerRow(boardSize);
        return boxesPerRow * boxesPerRow;
    }

    public int boxIndex(int row, int col) {
        int boxSize = boxDim(size);
        return (row / boxSize) * boxesPerRow(size) + (col / boxSize);
    }

    public boolean isPlayableValue(int value) {
        return DIGIT_FREQUENCIES[value] != 0;
    }

    public int getDigitFrequency(int digit) {
        if (!isPlayableValue(digit)) {
            return 0;
        }
        return DIGIT_FREQUENCIES[digit];
    }

    public int[] getUniqueDigits() {
        return UNIQUE_DIGITS.clone();
    }

    public int getBoxDim() {
        return (int) Math.sqrt(size);
    }

    public int getSize() {
        return size;
    }
}
