package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NakedSubsetStrategy extends SubsetSearchStrategy<BoardPosition> {
    public NakedSubsetStrategy(int subsetSize) {
        super(subsetSize);
    }

    @Override
    protected Move evaluateSubset(SolverSearchState state, PidokuRules rules, List<BoardPosition> empty,
                                  List<BoardPosition> subset, UnitType type, int index) {
        Set<Integer> union = new HashSet<>();
        for (BoardPosition pos : subset) {
            union.addAll(SearchUtils.getCandidates(state, pos.getRow(), pos.getCol()));
        }

        int totalRemainingFreq = 0;
        for (int digit : union) {
            totalRemainingFreq += getRemainingFrequency(state, rules, digit, type, index);
        }
        if (totalRemainingFreq != getSubsetSize()) {
            return null;
        }

        // Eliminate the subset's digits from all other cells in this unit.
        List<Move.Candidate> eliminations = new ArrayList<>();
        for (BoardPosition pos : empty) {
            if (subset.contains(pos)) continue;
            for (int value : union) {
                if (SearchUtils.isCandidate(state, pos, value)) {
                    eliminations.add(new Move.Candidate(pos, value));
                }
            }
        }
        return Move.eliminating(eliminations);
    }

    @Override
    protected List<BoardPosition> getCombinationPool(SolverSearchState state, List<BoardPosition> empty) {
        return empty;
    }
}
