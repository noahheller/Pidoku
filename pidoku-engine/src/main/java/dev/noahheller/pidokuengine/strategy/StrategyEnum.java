package dev.noahheller.pidokuengine.strategy;

public enum StrategyEnum {
    // Beginner
    // occurs when a specific CELL on has only one candidate left.
    NAKED_SINGLE(1,1, Tier.BEGINNER, new NakedSingleStrategy()),
    // occurs when a specific DIGIT can only be placed in one cell
    HIDDEN_SINGLE(2,2, Tier.BEGINNER, new HiddenSingleStrategy()),
    // occurs when there are two cells in the same group have the same two candidates and only those candidates
    NAKED_PAIR(5,2, Tier.EASY, new NakedSubsetStrategy(2)),
    // locked candidates / intersection removal (pointing/claiming)
    INTERSECTION_REMOVAL(6,3, Tier.EASY, new IntersectionRemovalStrategy()),
    // occurs when there are two cells in the same group such that two candidates only appear in those two cells. All other candidates for those cells can be eliminated
    HIDDEN_PAIR(8, 4, Tier.EASY, new HiddenSubsetStrategy(2)),

    NAKED_TRIPLE(9,4, Tier.MEDIUM, new NakedSubsetStrategy(3)),
    HIDDEN_TRIPLE(14,6, Tier.MEDIUM, new HiddenSubsetStrategy(3)),
    NAKED_QUAD(15, 7, Tier.HARD, new NakedSubsetStrategy(4)),
    HIDDEN_QUAD(22,10, Tier.HARD, new HiddenSubsetStrategy(4)),
    // X-Wing (frequency=1 digits)
    X_WING(18,8, Tier.HARD, new XWingStrategy()),
    // W-Wing (frequency=1 digits)
    W_WING(28,14, Tier.EXPERT, new WWingStrategy());

    private final int baseScore;
    private final int firstTimeScore;
    private final Tier tier;
    private final Strategy strategy;

    StrategyEnum(int firstTimeScore, int baseScore, Tier tier, Strategy strategy) {
        this.baseScore = baseScore;
        this.firstTimeScore = firstTimeScore;
        this.tier = tier;
        this.strategy = strategy;
    }

    public int getFirstTimeScore() {
        return firstTimeScore;
    }

    public int getBaseScore() {
        return baseScore;
    }

    public Tier getTier() {
        return tier;
    }

    public Strategy getStrategy() {
        return strategy;
    }
}
