package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NakedSubsetStrategy extends SubsetSearchStrategy<BoardPosition> {
    public NakedSubsetStrategy(int subsetSize) {
        super(subsetSize);
    }

    @Override
    protected StrategyResult evaluateSubset(SolverSearchState state, PidokuRules rules, List<BoardPosition> empty, List<BoardPosition> subset, UnitType type, int index) {
        Set<Integer> union = new HashSet<>();

        for (BoardPosition pos : subset) {
            union.addAll(SearchUtils.getCandidates(state, pos.getRow(), pos.getCol()));
        }

        // Compute total remaining frequency using the clean, inherited helper method
        int totalRemainingFreq = 0;
        for (int digit : union) {
            totalRemainingFreq += getRemainingFrequency(state, rules, digit, type, index);
        }

        if (totalRemainingFreq != getSubsetSize()) {
            return StrategyResult.FAILED;
        }

        int eliminations = 0;

        // Eliminate candidates from all other cells in this house
        for (BoardPosition pos : empty) {
            if (subset.contains(pos)) {
                continue;
            }
            for (int value : union) {
                if (SearchUtils.isCandidate(state, pos, value)) {
                    state.eliminate(pos.getRow(), pos.getCol(), value);
                    eliminations++;
                }
            }
        }

        if (eliminations > 0) {
            return StrategyResult.eliminated(eliminations);
        }
        return StrategyResult.FAILED;
    }

    @Override
    protected List<BoardPosition> getCombinationPool(SolverSearchState state, List<BoardPosition> empty) {
        return empty;
    }
}
