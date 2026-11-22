package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Contains a strategic action.
 * Either a single placement or a set of candidate eliminations.
 * Used to separate finding moves and applying them so the solver can see every
 * option available at a step before committing to one.
 *
 * <p>A move can also carry its {@link Reason}: the pattern that justifies it, which
 * {@code explain.MoveExplainer} turns into something a player can follow. The reason doesn't take
 * part in equality, so the same deduction found two ways is still one move.
 */
public final class Move {

    /** A digit in a cell, used for eliminations. */
    public record Candidate(BoardPosition position, int value) {}

    /**
     * Why a move holds, as the strategy that found it saw it.
     *
     * @param units  the units the pattern lives in, in the order the strategy reasons about them
     *               (see each strategy for what the order means)
     * @param cells  the cells that form the pattern, e.g. a naked pair's two cells
     * @param digits the digits the pattern is about, e.g. the pair's two digits
     */
    public record Reason(List<Unit> units, List<BoardPosition> cells, List<Integer> digits) {

        public Reason {
            units = List.copyOf(units);
            cells = List.copyOf(cells);
            digits = List.copyOf(digits);
        }
    }

    private final BoardPosition position;
    private final int value;
    private final List<Candidate> eliminations;
    private final Reason reason;

    private Move(BoardPosition position, int value, List<Candidate> eliminations, Reason reason) {
        this.position = position;
        this.value = value;
        this.eliminations = eliminations;
        this.reason = reason;
    }

    public static Move placement(BoardPosition position, int value) {
        return new Move(Objects.requireNonNull(position), value, List.of(), null);
    }

    /** @return a move eliminating every candidate given (duplicates removed), or null if there are none. */
    public static Move eliminating(Collection<Candidate> candidates) {
        if (candidates.isEmpty()) return null;
        return new Move(null, 0, List.copyOf(new LinkedHashSet<>(candidates)), null);
    }

    /** This move with the pattern that justifies it. */
    public Move because(List<Unit> units, List<BoardPosition> cells, List<Integer> digits) {
        return new Move(position, value, eliminations, new Reason(units, cells, digits));
    }

    public boolean isPlacement() {
        return position != null;
    }

    /** The placed cell, or null for an elimination move. */
    public BoardPosition getPosition() {
        return position;
    }

    /** The placed digit; only meaningful for a placement. */
    public int getValue() {
        return value;
    }

    public List<Candidate> getEliminations() {
        return eliminations;
    }

    /** Why the move holds, or null if the strategy didn't say. */
    public Reason getReason() {
        return reason;
    }

    public StrategyResult applyTo(SolverSearchState state) {
        if (isPlacement()) {
            state.place(position, value);
            return StrategyResult.placed(position, value);
        }
        for (Candidate c : eliminations) {
            state.eliminate(c.position().getRow(), c.position().getCol(), c.value());
        }
        return StrategyResult.eliminated(eliminations.size());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Move other
                && value == other.value
                && Objects.equals(position, other.position)
                && Objects.equals(new LinkedHashSet<>(eliminations), new LinkedHashSet<>(other.eliminations));
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, value, new LinkedHashSet<>(eliminations));
    }

    @Override
    public String toString() {
        return isPlacement() ? "place " + value + " at " + position : "eliminate " + eliminations;
    }
}
