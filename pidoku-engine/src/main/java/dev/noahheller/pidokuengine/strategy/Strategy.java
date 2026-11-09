package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.List;

public interface Strategy {
    /**
     * Finds every move this strategy can make on the current board.
     * Must not modify {@code state}. Moves may overlap (the same deduction seen
     * from a row and a box, for example).
     */
    List<Move> findAll(SolverSearchState state);

    /** Applies the first move found, if any. */
    default StrategyResult apply(SolverSearchState state) {
        List<Move> moves = findAll(state);
        return moves.isEmpty() ? StrategyResult.FAILED : moves.get(0).applyTo(state);
    }
}
