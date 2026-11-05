package dev.noahheller.pidokuengine;

public class BoardPosition {
    private final int row;
    private final int col;

    public BoardPosition(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

}
