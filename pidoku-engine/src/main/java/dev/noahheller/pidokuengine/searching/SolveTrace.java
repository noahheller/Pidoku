package dev.noahheller.pidokuengine.searching;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.StrategyResult;
import dev.noahheller.pidokuengine.strategy.Tier;
import dev.noahheller.pidokuengine.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SolveTrace {

    /** A step is a bottleneck when this many cells or fewer could be filled with a single... */
    public static final int BOTTLENECK_OPTIONS = 2;
    /** ...while at least this many cells were still empty (the last few cells don't count). */
    public static final int BOTTLENECK_MIN_EMPTY = 10;

    public static class Step {
        private final StrategyEnum strategy;
        private final StrategyResult.Type type;
        private final BoardPosition position;
        private final Integer value;
        private final int eliminationCount;
        private final double cost;
        private final int options;

        private Step(StrategyEnum strategy, StrategyResult result, double cost, int options) {
            this.strategy = strategy;
            this.type = result.type;
            this.position = result.getPosition();
            this.value = result.getValue();
            this.eliminationCount = result.eliminationCount;
            this.cost = cost;
            this.options = options;
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

        /** Difficulty of this step, see {@link DifficultyScorer}. */
        public double getCost() {
            return cost;
        }

        /** Cells that could be filled with a single when this step was taken (0 if it needed an elimination). */
        public int getOptions() {
            return options;
        }

        public boolean isPlacement() {
            return type == StrategyResult.Type.PLACED;
        }
    }

    public boolean solved = false;

    public boolean requiresGuessing = false;

    private final Map<StrategyEnum, Integer> useCounts = new EnumMap<>(StrategyEnum.class);

    private StrategyEnum hardestStrategy = null;

    private final List<Step> steps = new ArrayList<>();
    private final List<Double> stepCosts = new ArrayList<>();
    private int placementCount = 0;
    private int bottleneckSteps = 0;
    private final List<Integer> stepOptions = new ArrayList<>();
    private int initialEmptyCells = -1;

    /**
     * @param options    cells that could be filled with a single at this step (0 if it needed an elimination)
     * @param emptyCells cells still empty before this step
     */
    public void record(StrategyEnum strategy, StrategyResult result, double cost, int options, int emptyCells) {
        useCounts.merge(strategy, 1, Integer::sum);
        steps.add(new Step(strategy, result, cost, options));
        stepCosts.add(cost);
        stepOptions.add(options);
        if (initialEmptyCells < 0) initialEmptyCells = emptyCells;
        if (options <= BOTTLENECK_OPTIONS && emptyCells >= BOTTLENECK_MIN_EMPTY) {
            bottleneckSteps++;
        }
        if (result.type == StrategyResult.Type.PLACED) {
            placementCount++;
        }
        if (hardestStrategy == null
                || strategy.getTier().isHarderThan(hardestStrategy.getTier())
                || (strategy.getTier() == hardestStrategy.getTier()
                && strategy.getCost() > hardestStrategy.getCost())) {
            hardestStrategy = strategy;
        }
    }

    public int getTotalScore()                      { return DifficultyScorer.score(stepCosts, stepOptions, Math.max(0, initialEmptyCells), placementCount); }
    public int getTotalSteps()                      { return steps.size(); }
    public StrategyEnum getHardestStrategy()        { return hardestStrategy; }
    public List<Step> getSteps()                    { return Collections.unmodifiableList(steps); }
    public int getPlacementCount()                  { return placementCount; }
    public int getBottleneckSteps()                 { return bottleneckSteps; }

    public double getMaxStepCost() {
        return stepCosts.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    /**
     * Mean number of cells fillable with a single over the first 25 steps (Pelánek's
     * "dependency" metric). Lower means the puzzle is narrower.
     */
    public double getDependency() {
        return DifficultyScorer.dependency(stepOptions);
    }

    public int getUseCount(StrategyEnum s) {
        return useCounts.getOrDefault(s, 0);
    }

    public Map<StrategyEnum, Integer> getAllUseCounts() {
        return Collections.unmodifiableMap(useCounts);
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
                "SolveTrace{solved=%b, guessing=%b, score=%d, steps=%d, hardest=%s, maxStep=%.2f, dependency=%.2f, bottlenecks=%d, counts=%s}",
                solved, requiresGuessing, getTotalScore(), steps.size(), hardestStrategy,
                getMaxStepCost(), getDependency(), bottleneckSteps, useCounts);
    }
}
