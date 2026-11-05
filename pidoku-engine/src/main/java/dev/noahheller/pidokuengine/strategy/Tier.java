package dev.noahheller.pidokuengine.strategy;

public enum Tier {
    BEGINNER, EASY, MEDIUM, HARD, EXPERT;
    public boolean isHarderThan(Tier other) {
        return this.ordinal() > other.ordinal();
    }
}
