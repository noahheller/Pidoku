package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.List;

public class IntersectionRemovalStrategy implements Strategy {
    @Override
    public StrategyResult apply(SolverSearchState state) {
        int size = state.getSize();
        PidokuRules rules = state.getRules();
        int[] digits = rules.getUniqueDigits();
        int totalBoxes = rules.totalBoxes(size);

        for (int digit : digits) {
            // Pointing: box -> row/col
            for (int box = 0; box < totalBoxes; box++) {
                int boxRemaining = rules.getDigitFrequency(digit) - state.getBoxDigitCount(box, digit);
                if (boxRemaining <= 0) continue;

                List<BoardPosition> candidates = candidatesInBox(state, box, digit);
                if (candidates.isEmpty()) continue;

                int row = candidates.get(0).getRow();
                int col = candidates.get(0).getCol();
                boolean sameRow = true;
                boolean sameCol = true;
                for (BoardPosition pos : candidates) {
                    if (pos.getRow() != row) sameRow = false;
                    if (pos.getCol() != col) sameCol = false;
                    if (!sameRow && !sameCol) break;
                }

                if (sameRow) {
                    int rowRemaining = rules.getDigitFrequency(digit) - state.getRowDigitCount(row, digit);
                    if (rowRemaining == boxRemaining) {
                        int eliminations = eliminateFromRowOutsideBox(state, row, box, digit);
                        if (eliminations > 0) return StrategyResult.eliminated(eliminations);
                    }
                }

                if (sameCol) {
                    int colRemaining = rules.getDigitFrequency(digit) - state.getColDigitCount(col, digit);
                    if (colRemaining == boxRemaining) {
                        int eliminations = eliminateFromColOutsideBox(state, col, box, digit);
                        if (eliminations > 0) return StrategyResult.eliminated(eliminations);
                    }
                }
            }

            // Claiming: row/col -> box
            for (int row = 0; row < size; row++) {
                int rowRemaining = rules.getDigitFrequency(digit) - state.getRowDigitCount(row, digit);
                if (rowRemaining <= 0) continue;

                List<BoardPosition> candidates = candidatesInRow(state, row, digit);
                if (candidates.isEmpty()) continue;

                int box = rules.boxIndex(candidates.get(0).getRow(), candidates.get(0).getCol());
                boolean sameBox = true;
                for (BoardPosition pos : candidates) {
                    if (rules.boxIndex(pos.getRow(), pos.getCol()) != box) {
                        sameBox = false;
                        break;
                    }
                }
                if (sameBox) {
                    int boxRemaining = rules.getDigitFrequency(digit) - state.getBoxDigitCount(box, digit);
                    if (boxRemaining == rowRemaining) {
                        int eliminations = eliminateFromBoxOutsideRow(state, box, row, digit);
                        if (eliminations > 0) return StrategyResult.eliminated(eliminations);
                    }
                }
            }

            for (int col = 0; col < size; col++) {
                int colRemaining = rules.getDigitFrequency(digit) - state.getColDigitCount(col, digit);
                if (colRemaining <= 0) continue;

                List<BoardPosition> candidates = candidatesInCol(state, col, digit);
                if (candidates.isEmpty()) continue;

                int box = rules.boxIndex(candidates.get(0).getRow(), candidates.get(0).getCol());
                boolean sameBox = true;
                for (BoardPosition pos : candidates) {
                    if (rules.boxIndex(pos.getRow(), pos.getCol()) != box) {
                        sameBox = false;
                        break;
                    }
                }
                if (sameBox) {
                    int boxRemaining = rules.getDigitFrequency(digit) - state.getBoxDigitCount(box, digit);
                    if (boxRemaining == colRemaining) {
                        int eliminations = eliminateFromBoxOutsideCol(state, box, col, digit);
                        if (eliminations > 0) return StrategyResult.eliminated(eliminations);
                    }
                }
            }
        }

        return StrategyResult.FAILED;
    }

    private List<BoardPosition> candidatesInRow(SolverSearchState state, int row, int digit) {
        List<BoardPosition> empty = SearchUtils.emptyCellsInRow(state, row);
        List<BoardPosition> candidates = new ArrayList<>();
        for (BoardPosition pos : empty) {
            if (SearchUtils.isCandidate(state, pos, digit)) {
                candidates.add(pos);
            }
        }
        return candidates;
    }

    private List<BoardPosition> candidatesInCol(SolverSearchState state, int col, int digit) {
        List<BoardPosition> empty = SearchUtils.emptyCellsInCol(state, col);
        List<BoardPosition> candidates = new ArrayList<>();
        for (BoardPosition pos : empty) {
            if (SearchUtils.isCandidate(state, pos, digit)) {
                candidates.add(pos);
            }
        }
        return candidates;
    }

    private List<BoardPosition> candidatesInBox(SolverSearchState state, int boxIndex, int digit) {
        List<BoardPosition> empty = SearchUtils.emptyCellsInBox(state, boxIndex);
        List<BoardPosition> candidates = new ArrayList<>();
        for (BoardPosition pos : empty) {
            if (SearchUtils.isCandidate(state, pos, digit)) {
                candidates.add(pos);
            }
        }
        return candidates;
    }

    private int eliminateFromRowOutsideBox(SolverSearchState state, int row, int box, int digit) {
        int eliminations = 0;
        PidokuRules rules = state.getRules();
        for (int col = 0; col < state.getSize(); col++) {
            if (rules.boxIndex(row, col) == box) continue;
            if (SearchUtils.isCandidate(state, row, col, digit)) {
                state.eliminate(row, col, digit);
                eliminations++;
            }
        }
        return eliminations;
    }

    private int eliminateFromColOutsideBox(SolverSearchState state, int col, int box, int digit) {
        int eliminations = 0;
        PidokuRules rules = state.getRules();
        for (int row = 0; row < state.getSize(); row++) {
            if (rules.boxIndex(row, col) == box) continue;
            if (SearchUtils.isCandidate(state, row, col, digit)) {
                state.eliminate(row, col, digit);
                eliminations++;
            }
        }
        return eliminations;
    }

    private int eliminateFromBoxOutsideRow(SolverSearchState state, int box, int row, int digit) {
        int eliminations = 0;
        List<BoardPosition> empty = SearchUtils.emptyCellsInBox(state, box);
        for (BoardPosition pos : empty) {
            if (pos.getRow() == row) continue;
            if (SearchUtils.isCandidate(state, pos, digit)) {
                state.eliminate(pos.getRow(), pos.getCol(), digit);
                eliminations++;
            }
        }
        return eliminations;
    }

    private int eliminateFromBoxOutsideCol(SolverSearchState state, int box, int col, int digit) {
        int eliminations = 0;
        List<BoardPosition> empty = SearchUtils.emptyCellsInBox(state, box);
        for (BoardPosition pos : empty) {
            if (pos.getCol() == col) continue;
            if (SearchUtils.isCandidate(state, pos, digit)) {
                state.eliminate(pos.getRow(), pos.getCol(), digit);
                eliminations++;
            }
        }
        return eliminations;
    }
}
