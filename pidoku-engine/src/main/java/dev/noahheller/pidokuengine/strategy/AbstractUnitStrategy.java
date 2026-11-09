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

public abstract class AbstractUnitStrategy implements Strategy {

    public enum UnitType {
        ROW, COLUMN, BOX
    }

    private final Set<UnitType> unitTypes;

    /** @param unitTypes the units to search; all of them if none are given. */
    protected AbstractUnitStrategy(UnitType... unitTypes) {
        this.unitTypes = unitTypes.length == 0
                ? EnumSet.allOf(UnitType.class)
                : EnumSet.copyOf(Arrays.asList(unitTypes));
    }

    @Override
    public List<Move> findAll(SolverSearchState state) {
        int size = state.getSize();
        PidokuRules rules = state.getRules();
        List<Move> moves = new ArrayList<>();

        if (unitTypes.contains(UnitType.ROW)) {
            for (int r = 0; r < size; r++) {
                List<BoardPosition> cells = SearchUtils.emptyCellsInRow(state, r);
                if (!cells.isEmpty()) processUnit(state, rules, cells, UnitType.ROW, r, moves);
            }
        }

        if (unitTypes.contains(UnitType.COLUMN)) {
            for (int c = 0; c < size; c++) {
                List<BoardPosition> cells = SearchUtils.emptyCellsInCol(state, c);
                if (!cells.isEmpty()) processUnit(state, rules, cells, UnitType.COLUMN, c, moves);
            }
        }

        if (unitTypes.contains(UnitType.BOX)) {
            int totalBoxes = rules.totalBoxes(size);
            for (int b = 0; b < totalBoxes; b++) {
                List<BoardPosition> cells = SearchUtils.emptyCellsInBox(state, b);
                if (!cells.isEmpty()) processUnit(state, rules, cells, UnitType.BOX, b, moves);
            }
        }

        return moves;
    }

    /** Adds every move found in this unit to {@code out}. Must not modify {@code state}. */
    protected abstract void processUnit(SolverSearchState state, PidokuRules rules, List<BoardPosition> emptyCells,
                                        UnitType type, int index, List<Move> out);

    /**
     * calculate how many times a digit still needs to be placed inside a specific unit according to Pidoku frequency rules.
     */
    protected int getRemainingFrequency(SolverSearchState state, PidokuRules rules, int digit, UnitType type, int index) {
        int placedCount = switch (type) {
            case ROW -> state.getRowDigitCount(index, digit);
            case COLUMN -> state.getColDigitCount(index, digit);
            case BOX -> state.getBoxDigitCount(index, digit);
        };
        return rules.getDigitFrequency(digit) - placedCount;
    }
}
