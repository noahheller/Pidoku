package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.strategy.AbstractUnitStrategy.UnitType;

import java.util.ArrayList;
import java.util.List;

/**
 * One row, column or box. Indices are 0-based; boxes are numbered left to right, top to bottom.
 */
public record Unit(UnitType type, int index) {

    public static Unit row(int index) {
        return new Unit(UnitType.ROW, index);
    }

    public static Unit column(int index) {
        return new Unit(UnitType.COLUMN, index);
    }

    public static Unit box(int index) {
        return new Unit(UnitType.BOX, index);
    }

    /** Every cell of the unit, filled or not, in reading order. */
    public List<BoardPosition> cells(PidokuRules rules) {
        int size = rules.getSize();
        List<BoardPosition> cells = new ArrayList<>(size);
        switch (type) {
            case ROW -> {
                for (int col = 0; col < size; col++) cells.add(new BoardPosition(index, col));
            }
            case COLUMN -> {
                for (int row = 0; row < size; row++) cells.add(new BoardPosition(row, index));
            }
            case BOX -> {
                int dim = rules.boxDim(size);
                int startRow = (index / rules.boxesPerRow(size)) * dim;
                int startCol = (index % rules.boxesPerRow(size)) * dim;
                for (int row = startRow; row < startRow + dim; row++) {
                    for (int col = startCol; col < startCol + dim; col++) cells.add(new BoardPosition(row, col));
                }
            }
        }
        return cells;
    }

    public boolean contains(BoardPosition position, PidokuRules rules) {
        return switch (type) {
            case ROW -> position.getRow() == index;
            case COLUMN -> position.getCol() == index;
            case BOX -> rules.boxIndex(position.getRow(), position.getCol()) == index;
        };
    }

    /** The row, column and box a cell is in. */
    public static List<Unit> unitsOf(BoardPosition position, PidokuRules rules) {
        return List.of(row(position.getRow()), column(position.getCol()),
                box(rules.boxIndex(position.getRow(), position.getCol())));
    }
}
