package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;

import java.util.ArrayList;
import java.util.List;

/**
 * One row, column or box. Indices are 0-based; boxes are numbered left to right, top to bottom.
 * Every unit is a rectangle of the board ({@link #top}, {@link #left}, {@link #height},
 * {@link #width}): a row is 1 x size, a box 3 x 3 on a 9x9 board. Its cells are numbered 0 to
 * size - 1 in reading order: left to right, then top to bottom.
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

    /** Every unit of one type, in index order. */
    public static List<Unit> all(UnitType type, PidokuRules rules) {
        int count = type == UnitType.BOX ? rules.totalBoxes() : rules.getSize();
        List<Unit> units = new ArrayList<>(count);
        for (int i = 0; i < count; i++) units.add(new Unit(type, i));
        return units;
    }

    /** The unit's first row. */
    public int top(PidokuRules rules) {
        return switch (type) {
            case ROW -> index;
            case COLUMN -> 0;
            case BOX -> (index / rules.boxesPerRow()) * rules.boxDim();
        };
    }

    /** The unit's first column. */
    public int left(PidokuRules rules) {
        return switch (type) {
            case ROW -> 0;
            case COLUMN -> index;
            case BOX -> (index % rules.boxesPerRow()) * rules.boxDim();
        };
    }

    /** How many rows the unit spans. */
    public int height(PidokuRules rules) {
        return switch (type) {
            case ROW -> 1;
            case COLUMN -> rules.getSize();
            case BOX -> rules.boxDim();
        };
    }

    /** How many columns the unit spans. */
    public int width(PidokuRules rules) {
        return switch (type) {
            case ROW -> rules.getSize();
            case COLUMN -> 1;
            case BOX -> rules.boxDim();
        };
    }

    /** The row of the unit's {@code i}th cell. */
    public int rowOf(int i, PidokuRules rules) {
        return switch (type) {
            case ROW -> index;
            case COLUMN -> i;
            case BOX -> top(rules) + i / rules.boxDim();
        };
    }

    /** The column of the unit's {@code i}th cell. */
    public int colOf(int i, PidokuRules rules) {
        return switch (type) {
            case ROW -> i;
            case COLUMN -> index;
            case BOX -> left(rules) + i % rules.boxDim();
        };
    }

    /** The unit's {@code i}th cell. */
    public BoardPosition cell(int i, PidokuRules rules) {
        return new BoardPosition(rowOf(i, rules), colOf(i, rules));
    }

    /** Every cell of the unit, filled or not, in reading order. */
    public List<BoardPosition> cells(PidokuRules rules) {
        List<BoardPosition> cells = new ArrayList<>(rules.getSize());
        int top = top(rules);
        int left = left(rules);
        int bottom = top + height(rules);
        int right = left + width(rules);
        for (int row = top; row < bottom; row++) {
            for (int col = left; col < right; col++) cells.add(new BoardPosition(row, col));
        }
        return cells;
    }

    public boolean contains(BoardPosition position, PidokuRules rules) {
        return contains(position.getRow(), position.getCol(), rules);
    }

    public boolean contains(int row, int col, PidokuRules rules) {
        return switch (type) {
            case ROW -> row == index;
            case COLUMN -> col == index;
            case BOX -> rules.boxIndex(row, col) == index;
        };
    }

    /** The row, column and box a cell is in. */
    public static List<Unit> unitsOf(BoardPosition position, PidokuRules rules) {
        return List.of(row(position.getRow()), column(position.getCol()),
                box(rules.boxIndex(position.getRow(), position.getCol())));
    }
}
