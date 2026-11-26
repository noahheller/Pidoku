package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
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
                int mask = state.candidateMask(row, col);
                if (Integer.bitCount(mask) == 1) {
                    int digit = Integer.numberOfTrailingZeros(mask);
                    BoardPosition position = new BoardPosition(row, col);
                    moves.add(Move.placement(position, digit)
                            .because(Unit.unitsOf(position, state.getRules()), List.of(position), List.of(digit)));
                }
            }
        }
        return moves;
    }
}
