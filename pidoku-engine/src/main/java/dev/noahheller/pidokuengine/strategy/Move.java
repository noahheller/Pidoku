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
 */
public final class Move {

    /** A digit in a cell, used for eliminations. */
    public record Candidate(BoardPosition position, int value) {}

    private final BoardPosition position;
    private final int value;
    private final List<Candidate> eliminations;

    private Move(BoardPosition position, int value, List<Candidate> eliminations) {
        this.position = position;
        this.value = value;
        this.eliminations = eliminations;
    }

    public static Move placement(BoardPosition position, int value) {
        return new Move(Objects.requireNonNull(position), value, List.of());
    }

    /** @return a move eliminating every candidate given (duplicates removed), or null if there are none. */
    public static Move eliminating(Collection<Candidate> candidates) {
        if (candidates.isEmpty()) return null;
        return new Move(null, 0, List.copyOf(new LinkedHashSet<>(candidates)));
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
