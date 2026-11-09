package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.DifficultyScorer;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolveTrace;
import dev.noahheller.pidokuengine.searching.SolverSearchState;
import dev.noahheller.pidokuengine.strategy.Move;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.StrategyResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Solves like a person: at each step it looks at every move available and takes
 * the easiest one, recording how hard it was and how many alternatives existed.
 */
public class HumanPidokuSolver implements PidokuSolver {
    private final PidokuRules rules;

    public HumanPidokuSolver(PidokuRules rules) {
        this.rules = rules;
    }

    public PidokuPuzzle solve(int[][] puzzle) {
        SolverSearchState state = SolverSearchState.from(rules, puzzle);
        SolveTrace trace = new SolveTrace();

        // A clue that isn't a digit of this Pidoku, or one that breaks a row/column/box,
        // means there is nothing to solve. Without this the solver would work on a
        // half-loaded board and could report an impossible puzzle as solved.
        if (!state.isValid()) {
            trace.invalid = true;
            return new PidokuPuzzle(puzzle, trace);
        }

        while (!SearchUtils.isSolved(state)) {
            if (!takeStep(state, trace)) {
                trace.requiresGuessing = true;
                return new PidokuPuzzle(puzzle, trace);
            }
        }

        trace.solved = true;
        return new PidokuPuzzle(puzzle, trace);
    }

    private record Choice(StrategyEnum strategy, Move move, double cost) {}

    /** Makes the easiest available move. Returns false if no strategy can make progress. */
    private boolean takeStep(SolverSearchState state, SolveTrace trace) {
        // Placements first: every single counts as an option, and the cheapest one is taken.
        Choice best = null;
        Set<BoardPosition> placeableCells = new HashSet<>();
        for (StrategyEnum strategy : StrategyEnum.values()) {
            if (!strategy.placesDigits()) continue;
            for (Move move : strategy.getStrategy().findAll(state)) {
                placeableCells.add(move.getPosition());
                double cost = DifficultyScorer.moveCost(strategy, move, rules);
                if (best == null || cost < best.cost()) {
                    best = new Choice(strategy, move, cost);
                }
            }
        }
        // Options are the cells a single could fill; 0 when the step needs an elimination.
        int options = placeableCells.size();

        // No singles: fall back to the first elimination strategy that applies.
        if (best == null) {
            for (StrategyEnum strategy : StrategyEnum.values()) {
                if (strategy.placesDigits()) continue;
                List<Move> moves = strategy.getStrategy().findAll(state);
                if (moves.isEmpty()) continue;
                best = new Choice(strategy, moves.get(0), DifficultyScorer.moveCost(strategy, moves.get(0), rules));
                break;
            }
        }

        if (best == null) return false;

        int empty = SearchUtils.countEmpty(state);
        StrategyResult result = best.move().applyTo(state);
        trace.record(best.strategy(), result, best.cost(), options, empty);
        return true;
    }
}
