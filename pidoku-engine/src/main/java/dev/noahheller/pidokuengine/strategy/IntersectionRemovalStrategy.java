package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.List;

public class IntersectionRemovalStrategy implements Strategy {
    @Override
    public List<Move> findAll(SolverSearchState state) {
        int size = state.getSize();
        PidokuRules rules = state.getRules();
        int[] digits = rules.getUniqueDigits();
        int totalBoxes = rules.totalBoxes(size);
        List<Move> moves = new ArrayList<>();

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
                        addIfAny(moves, rowOutsideBox(state, row, box, digit));
                    }
                }

                if (sameCol) {
                    int colRemaining = rules.getDigitFrequency(digit) - state.getColDigitCount(col, digit);
                    if (colRemaining == boxRemaining) {
                        addIfAny(moves, colOutsideBox(state, col, box, digit));
                    }
                }
            }

            // Claiming: row/col -> box
            for (int row = 0; row < size; row++) {
                int rowRemaining = rules.getDigitFrequency(digit) - state.getRowDigitCount(row, digit);
                if (rowRemaining <= 0) continue;

                List<BoardPosition> candidates = candidatesInRow(state, row, digit);
                Integer box = commonBox(rules, candidates);
                if (box == null) continue;

                int boxRemaining = rules.getDigitFrequency(digit) - state.getBoxDigitCount(box, digit);
                if (boxRemaining == rowRemaining) {
                    addIfAny(moves, boxOutsideRow(state, box, row, digit));
                }
            }

            for (int col = 0; col < size; col++) {
                int colRemaining = rules.getDigitFrequency(digit) - state.getColDigitCount(col, digit);
                if (colRemaining <= 0) continue;

                List<BoardPosition> candidates = candidatesInCol(state, col, digit);
                Integer box = commonBox(rules, candidates);
                if (box == null) continue;

                int boxRemaining = rules.getDigitFrequency(digit) - state.getBoxDigitCount(box, digit);
                if (boxRemaining == colRemaining) {
                    addIfAny(moves, boxOutsideCol(state, box, col, digit));
                }
            }
        }

        return moves;
    }

    private static void addIfAny(List<Move> moves, List<Move.Candidate> eliminations) {
        Move move = Move.eliminating(eliminations);
        if (move != null) moves.add(move);
    }

    /** @return the box every candidate is in, or null if they span several boxes (or there are none). */
    private static Integer commonBox(PidokuRules rules, List<BoardPosition> candidates) {
        if (candidates.isEmpty()) return null;
        int box = rules.boxIndex(candidates.get(0).getRow(), candidates.get(0).getCol());
        for (BoardPosition pos : candidates) {
            if (rules.boxIndex(pos.getRow(), pos.getCol()) != box) return null;
        }
        return box;
    }

    private List<BoardPosition> candidatesInRow(SolverSearchState state, int row, int digit) {
        return withCandidate(state, SearchUtils.emptyCellsInRow(state, row), digit);
    }

    private List<BoardPosition> candidatesInCol(SolverSearchState state, int col, int digit) {
        return withCandidate(state, SearchUtils.emptyCellsInCol(state, col), digit);
    }

    private List<BoardPosition> candidatesInBox(SolverSearchState state, int boxIndex, int digit) {
        return withCandidate(state, SearchUtils.emptyCellsInBox(state, boxIndex), digit);
    }

    private static List<BoardPosition> withCandidate(SolverSearchState state, List<BoardPosition> cells, int digit) {
        List<BoardPosition> candidates = new ArrayList<>();
        for (BoardPosition pos : cells) {
            if (SearchUtils.isCandidate(state, pos, digit)) {
                candidates.add(pos);
            }
        }
        return candidates;
    }

    private List<Move.Candidate> rowOutsideBox(SolverSearchState state, int row, int box, int digit) {
        List<Move.Candidate> eliminations = new ArrayList<>();
        PidokuRules rules = state.getRules();
        for (int col = 0; col < state.getSize(); col++) {
            if (rules.boxIndex(row, col) == box) continue;
            if (SearchUtils.isCandidate(state, row, col, digit)) {
                eliminations.add(new Move.Candidate(new BoardPosition(row, col), digit));
            }
        }
        return eliminations;
    }

    private List<Move.Candidate> colOutsideBox(SolverSearchState state, int col, int box, int digit) {
        List<Move.Candidate> eliminations = new ArrayList<>();
        PidokuRules rules = state.getRules();
        for (int row = 0; row < state.getSize(); row++) {
            if (rules.boxIndex(row, col) == box) continue;
            if (SearchUtils.isCandidate(state, row, col, digit)) {
                eliminations.add(new Move.Candidate(new BoardPosition(row, col), digit));
            }
        }
        return eliminations;
    }

    private List<Move.Candidate> boxOutsideRow(SolverSearchState state, int box, int row, int digit) {
        List<Move.Candidate> eliminations = new ArrayList<>();
        for (BoardPosition pos : SearchUtils.emptyCellsInBox(state, box)) {
            if (pos.getRow() == row) continue;
            if (SearchUtils.isCandidate(state, pos, digit)) {
                eliminations.add(new Move.Candidate(pos, digit));
            }
        }
        return eliminations;
    }

    private List<Move.Candidate> boxOutsideCol(SolverSearchState state, int box, int col, int digit) {
        List<Move.Candidate> eliminations = new ArrayList<>();
        for (BoardPosition pos : SearchUtils.emptyCellsInBox(state, box)) {
            if (pos.getCol() == col) continue;
            if (SearchUtils.isCandidate(state, pos, digit)) {
                eliminations.add(new Move.Candidate(pos, digit));
            }
        }
        return eliminations;
    }
}
