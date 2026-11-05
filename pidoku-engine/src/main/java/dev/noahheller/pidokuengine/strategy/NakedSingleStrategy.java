package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.List;

public class NakedSingleStrategy implements Strategy {
    @Override
    public StrategyResult apply(SolverSearchState state) {
        int size = state.getSize();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (!state.isEmpty(row, col)) continue;
                List<Integer> cands = SearchUtils.getCandidates(state, row, col);
                if (cands.size() == 1) {
                    int value = cands.get(0);
                    state.place(row, col, value);
                    return StrategyResult.placed(new BoardPosition(row, col), value);
                }
            }
        }
        return StrategyResult.FAILED;
    }
}
