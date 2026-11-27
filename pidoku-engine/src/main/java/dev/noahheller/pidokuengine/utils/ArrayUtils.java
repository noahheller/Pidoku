package dev.noahheller.pidokuengine.utils;

public final class ArrayUtils {

    private ArrayUtils() {
    }

    /** A copy of {@code original} that shares none of its rows. */
    public static int[][] copy(int[][] original) {
        int[][] copy = new int[original.length][];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i].clone();
        }
        return copy;
    }
}
