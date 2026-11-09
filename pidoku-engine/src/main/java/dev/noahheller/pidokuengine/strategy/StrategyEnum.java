package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.strategy.AbstractUnitStrategy.UnitType;

/**
 * Strategies in the order a person tends to look for them, cheapest first.
 * {@code cost} is how hard one use is to spot; see DifficultyScorer for how
 * costs become a puzzle score.
 */
public enum StrategyEnum {
    // Beginner
    // A box with only one spot left for a digit.
    HIDDEN_SINGLE_BOX(1.0, Tier.BEGINNER, new HiddenSingleStrategy(UnitType.BOX)),
    // The same, in a row or column, which is harder to scan than a compact box.
    HIDDEN_SINGLE_LINE(1.5, Tier.BEGINNER, new HiddenSingleStrategy(UnitType.ROW, UnitType.COLUMN)),
    // occurs when a specific CELL on has only one candidate left.
    NAKED_SINGLE(2.0, Tier.BEGINNER, new NakedSingleStrategy()),

    // occurs when there are two cells in the same group have the same two candidates and only those candidates
    NAKED_PAIR(5, Tier.EASY, new NakedSubsetStrategy(2)),
    // Locked candidates / intersection removal (pointing/claiming).
    INTERSECTION_REMOVAL(6, Tier.EASY, new IntersectionRemovalStrategy()),
    // Digits in a unit that can only go in the same cells, clearing those cells' other candidates.
    HIDDEN_PAIR(8, Tier.EASY, new HiddenSubsetStrategy(2)),

    NAKED_TRIPLE(9, Tier.MEDIUM, new NakedSubsetStrategy(3)),
    HIDDEN_TRIPLE(14, Tier.MEDIUM, new HiddenSubsetStrategy(3)),
    NAKED_QUAD(15, Tier.HARD, new NakedSubsetStrategy(4)),
    // X-Wing (frequency=1 digits)
    X_WING(18, Tier.HARD, new XWingStrategy()),
    HIDDEN_QUAD(22, Tier.HARD, new HiddenSubsetStrategy(4)),
    // W-Wing (frequency=1 digits)
    W_WING(28, Tier.EXPERT, new WWingStrategy());

    private final double cost;
    private final Tier tier;
    private final Strategy strategy;

    StrategyEnum(double cost, Tier tier, Strategy strategy) {
        this.cost = cost;
        this.tier = tier;
        this.strategy = strategy;
    }

    public double getCost() {
        return cost;
    }

    public Tier getTier() {
        return tier;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    /** True for the strategies that place a digit (the singles). */
    public boolean placesDigits() {
        return this == HIDDEN_SINGLE_BOX || this == HIDDEN_SINGLE_LINE || this == NAKED_SINGLE;
    }
}
