package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.List;

public class NakedSingleStrategy implements Strategy {
    @Override
    public List<Move> findAll(SolverSearchState state) {
        int size = state.getSize();
        List<Move> moves = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (!state.isEmpty(row, col)) continue;
                List<Integer> cands = SearchUtils.getCandidates(state, row, col);
                if (cands.size() == 1) {
                    moves.add(Move.placement(new BoardPosition(row, col), cands.get(0)));
                }
            }
        }
        return moves;
    }
}
