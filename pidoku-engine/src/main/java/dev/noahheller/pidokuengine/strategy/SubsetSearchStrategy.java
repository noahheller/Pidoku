package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.List;

public abstract class SubsetSearchStrategy<T> extends AbstractUnitStrategy {
    private final int subsetSize;

    public SubsetSearchStrategy(int subsetSize) {
        this.subsetSize = subsetSize;
    }

    @Override
    protected void processUnit(SolverSearchState state, PidokuRules rules, List<BoardPosition> emptyCells,
                               Unit unit, List<Move> out) {
        List<T> pool = getCombinationPool(state, emptyCells);
        searchSubsets(state, rules, emptyCells, unit, 0, new ArrayList<>(), pool, out);
    }

    private void searchSubsets(SolverSearchState state, PidokuRules rules, List<BoardPosition> empty, Unit unit,
                               int start, List<T> subset, List<T> pool, List<Move> out) {
        if (subset.size() == subsetSize) {
            Move move = evaluateSubset(state, rules, empty, subset, unit);
            if (move != null) out.add(move);
            return;
        }
        for (int i = start; i < pool.size(); i++) {
            subset.add(pool.get(i));
            searchSubsets(state, rules, empty, unit, i + 1, subset, pool, out);
            subset.remove(subset.size() - 1);
        }
    }

    protected int getSubsetSize() {
        return subsetSize;
    }

    /** @return the eliminations this subset allows, or null if it isn't a valid subset or eliminates nothing. */
    protected abstract Move evaluateSubset(SolverSearchState state, PidokuRules rules, List<BoardPosition> empty,
                                           List<T> subset, Unit unit);

    protected abstract List<T> getCombinationPool(SolverSearchState state, List<BoardPosition> empty);
}
