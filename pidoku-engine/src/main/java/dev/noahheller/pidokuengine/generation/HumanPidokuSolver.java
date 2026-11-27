package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.explain.Explanation;
import dev.noahheller.pidokuengine.explain.MoveExplainer;
import dev.noahheller.pidokuengine.searching.DifficultyScorer;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolveTrace;
import dev.noahheller.pidokuengine.searching.SolverSearchState;
import dev.noahheller.pidokuengine.strategy.Move;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.StrategyResult;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Solves like a person: at each step it looks at every move available and takes
 * the easiest one, recording how hard it was and how many alternatives existed.
 */
public class HumanPidokuSolver {
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

    /**
     * A step the solver would take.
     *
     * @param options how many cells a single could fill at this point; 0 when the step needs an elimination
     */
    public record NextMove(StrategyEnum strategy, Move move, double cost, int options) {

        /** The step as a player can follow it, as of {@code before}, the board before it's made. */
        public Explanation explain(SolverSearchState before) {
            return MoveExplainer.explain(strategy, move, before);
        }
    }

    /**
     * The easiest move on this board, the one {@link #solve} would make next, or empty if no
     * strategy can make progress (the board is solved, or needs guessing). Doesn't change the board.
     */
    public static Optional<NextMove> nextMove(SolverSearchState state) {
        PidokuRules rules = state.getRules();
        // Placements first: every single counts as an option, and the cheapest one is taken.
        NextMove best = null;
        Set<BoardPosition> placeableCells = new HashSet<>();
        for (StrategyEnum strategy : StrategyEnum.values()) {
            if (!strategy.placesDigits()) continue;
            for (Move move : strategy.getStrategy().findAll(state)) {
                placeableCells.add(move.getPosition());
                double cost = DifficultyScorer.moveCost(strategy, move, rules);
                if (best == null || cost < best.cost()) {
                    best = new NextMove(strategy, move, cost, 0);
                }
            }
        }
        if (best != null) {
            return Optional.of(new NextMove(best.strategy(), best.move(), best.cost(), placeableCells.size()));
        }

        // No singles: fall back to the first elimination strategy that applies.
        for (StrategyEnum strategy : StrategyEnum.values()) {
            if (strategy.placesDigits()) continue;
            List<Move> moves = strategy.getStrategy().findAll(state);
            if (moves.isEmpty()) continue;
            return Optional.of(new NextMove(strategy, moves.getFirst(), DifficultyScorer.moveCost(strategy, moves.getFirst(), rules), 0));
        }
        return Optional.empty();
    }

    /** Makes the easiest available move. Returns false if no strategy can make progress. */
    private boolean takeStep(SolverSearchState state, SolveTrace trace) {
        Optional<NextMove> next = nextMove(state);
        if (next.isEmpty()) return false;
        NextMove best = next.get();
        int empty = SearchUtils.countEmpty(state);
        StrategyResult result = best.move().applyTo(state);
        trace.record(best.strategy(), result, best.cost(), best.options(), empty);
        return true;
    }
}
