package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.searching.SolveTrace;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.Tier;

import java.util.EnumMap;
import java.util.Map;

public class DifficultyProfile {

    public final String name;
    public final int minScore;
    public final int maxScore;
    /** The score the generator aims for; puzzles closest to it win. See {@link Builder#targetScore}. */
    public final int targetScore;
    public final Tier strategyTierCeiling;
    public final Tier strategyTierFloor;
    public final int minClues;
    public final int maxClues;
    public final Map<StrategyEnum, Integer> maxStrategyUses;

    private DifficultyProfile(Builder b) {
        this.name = b.name;
        this.minScore = b.minScore;
        this.maxScore = b.maxScore;
        this.targetScore = b.targetScore != null ? b.targetScore : defaultTarget(b.minScore, b.maxScore);
        this.minClues = b.minClues;
        this.maxClues = b.maxClues;
        this.strategyTierCeiling = b.ceiling;
        this.strategyTierFloor = b.floor;
        this.maxStrategyUses = java.util.Collections.unmodifiableMap(b.maxStrategyUses);
    }

    // Score windows are calibrated against DifficultyScorer (p10-p90 over ~100k generated puzzles):
    // singles-only 18-31 at 50-65 clues, 27-43 at 40-45, 40-69 at 25-30; pairs/intersections 66-120;
    // triples 120-130; quads/X-Wing 128-161; W-Wing 152+. Tier floors/ceilings separate the overlaps.
    public static DifficultyProfile trivial() {
        return new Builder("Trivial")
                .scoreRange(0, 32)
                .clueRange(50, 65)
                .ceiling(Tier.BEGINNER)
                .floor(Tier.BEGINNER)
                .build();
    }
    /** Between Trivial and Easy: singles only, with enough clues that there's usually more than one move available. */
    public static DifficultyProfile relaxing() {
        return new Builder("Relaxing")
                .scoreRange(25, 45)
                .clueRange(38, 45)
                .ceiling(Tier.BEGINNER)
                .floor(Tier.BEGINNER)
                .build();
    }

    public static DifficultyProfile easy() {
        return new Builder("Easy")
                .scoreRange(35, 70)
                .clueRange(30, 45)
                .ceiling(Tier.BEGINNER)
                .floor(Tier.BEGINNER)
                .build();
    }

    public static DifficultyProfile medium() {
        return new Builder("Medium")
                .scoreRange(65, 120)
                .clueRange(25, 40)
                .ceiling(Tier.EASY)
                .floor(Tier.EASY)
                .maxUses(StrategyEnum.NAKED_PAIR, 4)
                .maxUses(StrategyEnum.HIDDEN_PAIR, 3)
                .build();
    }

    public static DifficultyProfile hard() {
        return new Builder("Hard")
                .scoreRange(115, 170)
                .clueRange(10, 30)
                .ceiling(Tier.HARD)
                .floor(Tier.MEDIUM)
                .maxUses(StrategyEnum.NAKED_TRIPLE, 5)
                .maxUses(StrategyEnum.HIDDEN_TRIPLE, 4)
                .build();
    }

    public static DifficultyProfile fiendish() {
        return new Builder("Fiendish")
                .scoreRange(150, Integer.MAX_VALUE)
                // No upper bound, so aim near the median W-Wing puzzle rather than the hardest reachable.
                .targetScore(190)
                .clueRange(Integer.MIN_VALUE, 25)
                .ceiling(Tier.EXPERT)
                .floor(Tier.EXPERT)
                .build();
    }

    public static DifficultyProfile[] premadeProfiles() {
       return new DifficultyProfile[]{trivial(), relaxing(), easy(), medium(), hard(), fiendish()};
    }

    /**
     * Middle of the score window, or the minimum when the window has no upper bound
     * (the midpoint of an open-ended window is about a billion, which no puzzle reaches).
     */
    static int defaultTarget(int minScore, int maxScore) {
        if (maxScore == Integer.MAX_VALUE) return minScore;
        return (int) ((minScore + (long) maxScore) / 2);
    }

    /** How far {@code score} is from {@link #targetScore}; the generator prefers smaller. */
    public long distanceToTarget(int score) {
        return Math.abs((long) score - targetScore);
    }

    public boolean accepts(SolveTrace trace, int clueCount) {
        if (!trace.solved || trace.requiresGuessing) return false;

        if (clueCount >= 0 && (clueCount < minClues || clueCount > maxClues)) return false;

        if (trace.getTotalScore() < minScore || trace.getTotalScore() > maxScore) return false;

        if (trace.getHardestStrategy() != null
                && trace.getHardestStrategy().getTier().isHarderThan(strategyTierCeiling)) {
            return false;
        }

        if (!trace.usedTierOrHigher(strategyTierFloor)) return false;

        for (Map.Entry<StrategyEnum, Integer> entry : maxStrategyUses.entrySet()) {
            if (trace.getUseCount(entry.getKey()) > entry.getValue()) return false;
        }

        return true;
    }

    public static class Builder {
        private final String name;
        private int minScore = 0, maxScore = Integer.MAX_VALUE;
        private Integer targetScore = null;
        private Tier ceiling = Tier.EXPERT;
        private Tier floor = Tier.BEGINNER;
        private int minClues = 20;
        private int maxClues = 40;
        private final Map<StrategyEnum, Integer> maxStrategyUses = new EnumMap<>(StrategyEnum.class);

        public Builder(String name) {
            this.name = name;
        }

        public Builder scoreRange(int min, int max) {
            minScore = min;
            maxScore = max;
            return this;
        }

        /**
         * Score the generator should aim for. Defaults to the middle of the score range,
         * or to the minimum if the range has no upper bound.
         */
        public Builder targetScore(int target) {
            targetScore = target;
            return this;
        }

        public Builder clueRange(int min, int max) {
            minClues = min;
            maxClues = max;
            return this;
        }

        public Builder ceiling(Tier t) {
            ceiling = t;
            return this;
        }

        public Builder floor(Tier t) {
            floor = t;
            return this;
        }

        public Builder maxUses(StrategyEnum s, int max) {
            maxStrategyUses.put(s, max);
            return this;
        }

        /** @throws IllegalArgumentException if no puzzle could ever satisfy the settings */
        public DifficultyProfile build() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("profile needs a name");
            }
            if (minScore > maxScore) {
                throw new IllegalArgumentException(name + ": minScore " + minScore + " > maxScore " + maxScore);
            }
            if (minClues > maxClues) {
                throw new IllegalArgumentException(name + ": minClues " + minClues + " > maxClues " + maxClues);
            }
            if (ceiling == null || floor == null || floor.isHarderThan(ceiling)) {
                throw new IllegalArgumentException(name + ": strategy floor " + floor + " is above ceiling " + ceiling);
            }
            if (targetScore != null && (targetScore < minScore || targetScore > maxScore)) {
                throw new IllegalArgumentException(name + ": targetScore " + targetScore
                        + " is outside [" + minScore + ", " + maxScore + "]");
            }
            for (Map.Entry<StrategyEnum, Integer> entry : maxStrategyUses.entrySet()) {
                if (entry.getValue() < 0) {
                    throw new IllegalArgumentException(name + ": maxUses for " + entry.getKey() + " is negative");
                }
            }
            return new DifficultyProfile(this);
        }
    }

    @Override
    public String toString() {
        return String.format("DifficultyProfile{%s, score=[%d,%d] target=%d, clues=[%d,%d], ceiling=%s, floor=%s}",
                name, minScore, maxScore, targetScore, minClues, maxClues, strategyTierCeiling, strategyTierFloor);
    }
}
