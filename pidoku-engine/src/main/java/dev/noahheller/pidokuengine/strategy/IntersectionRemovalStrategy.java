package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.List;

/**
 * Locked candidates. Pointing: a box's spots for a digit all lie in one row (or column), so the
 * copies the box still needs go there, and the line's other cells can't have it. Claiming: the same
 * with a line's spots all in one box. Either way the two units must need the same number of copies.
 */
public class IntersectionRemovalStrategy implements Strategy {

    private static final UnitType[] LINES = {UnitType.ROW, UnitType.COLUMN};

    @Override
    public List<Move> findAll(SolverSearchState state) {
        PidokuRules rules = state.getRules();
        List<Move> moves = new ArrayList<>();

        for (int digit : rules.getUniqueDigits()) {
            // Pointing: box -> row/col
            for (Unit box : Unit.all(UnitType.BOX, rules)) {
                int boxRemaining = SearchUtils.remaining(state, box, digit);
                if (boxRemaining <= 0) continue;

                List<BoardPosition> candidates = SearchUtils.candidateCells(state, box, digit);
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
                    addIfMatched(state, moves, box, Unit.row(row), boxRemaining, candidates, digit);
                }
                if (sameCol) {
                    addIfMatched(state, moves, box, Unit.column(col), boxRemaining, candidates, digit);
                }
            }

            // Claiming: row/col -> box
            for (UnitType lineType : LINES) {
                for (Unit line : Unit.all(lineType, rules)) {
                    int lineRemaining = SearchUtils.remaining(state, line, digit);
                    if (lineRemaining <= 0) continue;

                    List<BoardPosition> candidates = SearchUtils.candidateCells(state, line, digit);
                    Integer box = commonBox(rules, candidates);
                    if (box == null) continue;

                    addIfMatched(state, moves, line, Unit.box(box), lineRemaining, candidates, digit);
                }
            }
        }

        return moves;
    }

    /**
     * Clears the digit from {@code target} outside {@code source}, if the target needs as many more
     * copies as the source does. The reason's units are the source, whose candidates all fall in
     * the intersection, then the target: box then line for pointing, line then box for claiming.
     */
    private static void addIfMatched(SolverSearchState state, List<Move> moves, Unit source, Unit target,
                                     int sourceRemaining, List<BoardPosition> candidates, int digit) {
        if (SearchUtils.remaining(state, target, digit) != sourceRemaining) return;
        Move move = Move.eliminating(outside(state, target, source, digit));
        if (move != null) moves.add(move.because(List.of(source, target), candidates, List.of(digit)));
    }

    /** The digit's candidates in {@code target} but not in {@code source}, in reading order. */
    private static List<Move.Candidate> outside(SolverSearchState state, Unit target, Unit source, int digit) {
        List<Move.Candidate> eliminations = new ArrayList<>();
        for (BoardPosition pos : SearchUtils.candidateCells(state, target, digit)) {
            if (!source.contains(pos, state.getRules())) {
                eliminations.add(new Move.Candidate(pos, digit));
            }
        }
        return eliminations;
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
}
