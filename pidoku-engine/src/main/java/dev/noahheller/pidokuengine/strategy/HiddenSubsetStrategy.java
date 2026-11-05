package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.*;

public class HiddenSubsetStrategy extends SubsetSearchStrategy<Integer> {
    public HiddenSubsetStrategy(int subsetSize) {
        super(subsetSize);
    }

    @Override
    protected StrategyResult evaluateSubset(SolverSearchState state, PidokuRules rules, List<BoardPosition> empty, List<Integer> subset, UnitType type, int index) {
        Set<Integer> cellSet = new LinkedHashSet<>();
        for (int v : subset) {
            boolean found = false;
            for (int i = 0; i < empty.size(); i++) {
                BoardPosition p = empty.get(i);
                if (SearchUtils.isCandidate(state, p.getRow(), p.getCol(), v)) {
                    cellSet.add(i);
                    found = true;
                }
            }
            if (!found) {
                return StrategyResult.FAILED;
            }
        }

        int totalRemainingFreq = 0;
        for (int digit : subset) {
            totalRemainingFreq += getRemainingFrequency(state, rules, digit, type, index);
        }

        if (totalRemainingFreq != cellSet.size()) {
            return StrategyResult.FAILED;
        }

        int eliminations = 0;

        for (int cellIndex : cellSet) {
            BoardPosition p = empty.get(cellIndex);
            List<Integer> cands = SearchUtils.getCandidates(state, p.getRow(), p.getCol());

            for (int v : cands) {
                if (!subset.contains(v)) {
                    state.eliminate(p.getRow(), p.getCol(), v);
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
    protected List<Integer> getCombinationPool(SolverSearchState state, List<BoardPosition> empty) {
        int[] uniqueDigits = state.getRules().getUniqueDigits();
        List<Integer> pool = new ArrayList<>(uniqueDigits.length);
        for (int digit : uniqueDigits) {
            pool.add(digit);
        }
        return pool;
    }
}
