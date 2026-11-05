package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.List;

public abstract class SubsetSearchStrategy<T> extends AbstractUnitStrategy{
    private final int subsetSize;

    public SubsetSearchStrategy(int subsetSize) {
        this.subsetSize = subsetSize;
    }

    @Override
    protected StrategyResult processUnit(SolverSearchState state, PidokuRules rules, List<BoardPosition> emptyCells, UnitType type, int index) {
        List<T> pool = getCombinationPool(state, emptyCells);
        return searchSubsets(state, rules, emptyCells, type, index, 0, new ArrayList<>(), pool);
    }

    private StrategyResult searchSubsets(SolverSearchState state, PidokuRules rules, List<BoardPosition> empty, UnitType type, int index, int start, List<T> subset, List<T> pool) {
        if (subset.size() == subsetSize) {
            return evaluateSubset(state, rules, empty, subset, type, index);
        }
        for (int i = start; i < pool.size(); i++) {
            subset.add(pool.get(i));

            StrategyResult result = searchSubsets(state, rules, empty, type, index, i + 1, subset, pool);
            if (result.madeProgress()) {
                return result;
            }
            subset.remove(subset.size() - 1);
        }
        return StrategyResult.FAILED;
    }

    protected int getSubsetSize() {
        return subsetSize;
    }

    protected abstract StrategyResult evaluateSubset(SolverSearchState state, PidokuRules rules, List<BoardPosition> empty, List<T> subset, UnitType type, int index);
    protected abstract List<T> getCombinationPool(SolverSearchState state, List<BoardPosition> empty);
}
