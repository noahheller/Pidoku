package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** A strategy that looks at one unit at a time: rows, then columns, then boxes. */
public abstract class AbstractUnitStrategy implements Strategy {

    private final Set<UnitType> unitTypes;

    /** @param unitTypes the units to search; all of them if none are given. */
    protected AbstractUnitStrategy(UnitType... unitTypes) {
        this.unitTypes = unitTypes.length == 0
                ? EnumSet.allOf(UnitType.class)
                : EnumSet.copyOf(Arrays.asList(unitTypes));
    }

    @Override
    public List<Move> findAll(SolverSearchState state) {
        PidokuRules rules = state.getRules();
        List<Move> moves = new ArrayList<>();
        // An EnumSet iterates in declaration order: rows, then columns, then boxes.
        for (UnitType type : unitTypes) {
            for (Unit unit : Unit.all(type, rules)) {
                List<BoardPosition> cells = SearchUtils.emptyCells(state, unit);
                if (!cells.isEmpty()) processUnit(state, rules, cells, unit, moves);
            }
        }
        return moves;
    }

    /**
     * Adds every move found in this unit to {@code out}. Must not modify {@code state}.
     *
     * @param emptyCells the unit's empty cells, in reading order; never empty
     */
    protected abstract void processUnit(SolverSearchState state, PidokuRules rules, List<BoardPosition> emptyCells,
                                        Unit unit, List<Move> out);
}
