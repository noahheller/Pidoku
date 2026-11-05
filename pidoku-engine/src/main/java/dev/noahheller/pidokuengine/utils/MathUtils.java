package dev.noahheller.pidokuengine.utils;

public class MathUtils {
    public static boolean hasPerfectRoot(int n) {
        long sqrtN = (long) Math.sqrt(n);
        return (sqrtN * sqrtN) == n;
    }
}
