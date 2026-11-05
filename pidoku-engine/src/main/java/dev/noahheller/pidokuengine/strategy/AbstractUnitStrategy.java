package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.List;

public abstract class AbstractUnitStrategy implements Strategy{

    public enum UnitType {
        ROW, COLUMN, BOX
    }

    @Override
    public StrategyResult apply(SolverSearchState state) {
        int size = state.getSize();
        PidokuRules rules = state.getRules();

        for (int r = 0; r < size; r++) {
            List<BoardPosition> cells = SearchUtils.emptyCellsInRow(state, r);
            if (!cells.isEmpty()) {
                StrategyResult res = processUnit(state, rules, cells, UnitType.ROW, r);
                if (res.madeProgress()) return res;
            }
        }

        for (int c = 0; c < size; c++) {
            List<BoardPosition> cells = SearchUtils.emptyCellsInCol(state, c);
            if (!cells.isEmpty()) {
                StrategyResult res = processUnit(state, rules, cells, UnitType.COLUMN, c);
                if (res.madeProgress()) return res;
            }
        }

        int totalBoxes = rules.totalBoxes(size);
        for (int b = 0; b < totalBoxes; b++) {
            List<BoardPosition> cells = SearchUtils.emptyCellsInBox(state, b);
            if (!cells.isEmpty()) {
                StrategyResult res = processUnit(state, rules, cells, UnitType.BOX, b);
                if (res.madeProgress()) return res;
            }
        }

        return StrategyResult.FAILED;
    }

    protected abstract StrategyResult processUnit(SolverSearchState state, PidokuRules rules, List<BoardPosition> emptyCells, UnitType type, int index);

    /**
     * calculate how many times a digit still needs to be placed inside a specific unit according to Pidoku frequency rules.
     */
    protected int getRemainingFrequency(SolverSearchState state, PidokuRules rules, int digit, UnitType type, int index) {
        int placedCount;
        switch (type) {
            case ROW:
                placedCount = state.getRowDigitCount(index, digit);
                break;
            case COLUMN:
                placedCount = state.getColDigitCount(index, digit);
                break;
            case BOX:
                placedCount = state.getBoxDigitCount(index, digit);
                break;
            default:
                throw new IllegalArgumentException("Unknown UnitType: " + type);
        }
        return rules.getDigitFrequency(digit) - placedCount;
    }
}