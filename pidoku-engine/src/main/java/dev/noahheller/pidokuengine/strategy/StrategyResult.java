package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;

public class StrategyResult {

    public enum Type { PLACED, ELIMINATED, FAILED }

    public static final StrategyResult FAILED = new StrategyResult(Type.FAILED, 0, null, null);

    public final Type type;
    /** How many candidates were eliminated (0 for PLACED / FAILED). */
    public final int eliminationCount;
    private final BoardPosition position;
    private final Integer value;

    private StrategyResult(Type type, int eliminationCount, BoardPosition position, Integer value) {
        this.type = type;
        this.eliminationCount = eliminationCount;
        this.position = position;
        this.value = value;
    }

    public static StrategyResult placed(BoardPosition position, int value) {
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        return new StrategyResult(Type.PLACED, 0, position, value);
    }

    public static StrategyResult eliminated(int count) {
        if (count <= 0) return FAILED;
        return new StrategyResult(Type.ELIMINATED, count, null, null);
    }

    public boolean madeProgress() {
        return type != Type.FAILED;
    }

    public BoardPosition getPosition() {
        return position;
    }

    public Integer getValue() {
        return value;
    }
}
