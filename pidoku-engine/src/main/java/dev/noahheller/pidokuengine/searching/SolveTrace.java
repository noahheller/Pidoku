package dev.noahheller.pidokuengine.searching;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.StrategyResult;
import dev.noahheller.pidokuengine.strategy.Tier;
import dev.noahheller.pidokuengine.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;

public class SolveTrace {

    public static class Step {
        private final StrategyEnum strategy;
        private final StrategyResult.Type type;
        private final BoardPosition position;
        private final Integer value;
        private final int eliminationCount;

        private Step(StrategyEnum strategy, StrategyResult result) {
            this.strategy = strategy;
            this.type = result.type;
            this.position = result.getPosition();
            this.value = result.getValue();
            this.eliminationCount = result.eliminationCount;
        }

        public StrategyEnum getStrategy() {
            return strategy;
        }

        public StrategyResult.Type getType() {
            return type;
        }

        public BoardPosition getPosition() {
            return position;
        }

        public Integer getValue() {
            return value;
        }

        public int getEliminationCount() {
            return eliminationCount;
        }

        public boolean isPlacement() {
            return type == StrategyResult.Type.PLACED;
        }
    }

    public boolean solved = false;

    public boolean requiresGuessing = false;

    private int totalScore = 0;

    private final Map<StrategyEnum, Integer> useCounts = new EnumMap<>(StrategyEnum.class);

    private StrategyEnum hardestStrategy = null;

    private int totalSteps = 0;

    private final List<Step> steps = new ArrayList<>();
    private int placementCount = 0;

    public void record(StrategyEnum strategy, StrategyResult result) {
        totalScore += useCounts.containsKey(strategy) ? strategy.getBaseScore() : strategy.getFirstTimeScore();
        totalSteps++;
        useCounts.merge(strategy, 1, Integer::sum);
        steps.add(new Step(strategy, result));
        if (result.type.equals(StrategyResult.Type.PLACED)){
            placementCount++;
        }
        if (hardestStrategy == null
                || strategy.getTier().isHarderThan(hardestStrategy.getTier())
                || (strategy.getTier() == hardestStrategy.getTier()
                && strategy.getBaseScore() > hardestStrategy.getBaseScore())) {
            hardestStrategy = strategy;
        }
    }

    public int getTotalScore()                      { return totalScore; }
    public int getTotalSteps()                      { return totalSteps; }
    public StrategyEnum getHardestStrategy()            { return hardestStrategy; }
    public List<Step> getSteps()                    { return java.util.Collections.unmodifiableList(steps); }
    public int getPlacementCount()                      { return placementCount; }

    public int getUseCount(StrategyEnum s) {
        return useCounts.getOrDefault(s, 0);
    }

    public Map<StrategyEnum, Integer> getAllUseCounts() {
        return java.util.Collections.unmodifiableMap(useCounts);
    }

    public int[][] reconstructSolution(int[][] puzzle) {
        int[][] reconstructed = ArrayUtils.copy(puzzle);
        for (Step step : steps) {
            if (step.isPlacement()) {
                BoardPosition position = step.getPosition();
                reconstructed[position.getRow()][position.getCol()] = step.getValue();
            }
        }
        return reconstructed;
    }

    public boolean usedTierOrHigher(Tier tier) {
        if (hardestStrategy == null) return false;
        return hardestStrategy.getTier().ordinal() >= tier.ordinal();
    }

    @Override
    public String toString() {
        return String.format(
                "SolveTrace{solved=%b, guessing=%b, score=%d, steps=%d, hardest=%s, counts=%s}",
                solved, requiresGuessing, totalScore, totalSteps, hardestStrategy, useCounts);
    }
}
