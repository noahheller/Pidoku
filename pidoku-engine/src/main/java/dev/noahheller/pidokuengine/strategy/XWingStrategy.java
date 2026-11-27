package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.List;

/**
 * Two rows that each have only two spots left for a digit, in the same two columns: the digit is
 * in one corner of that rectangle in each row, so it can't be anywhere else in those columns. The
 * same with rows and columns swapped. Only for digits π has once.
 */
public class XWingStrategy implements Strategy {
    @Override
    public List<Move> findAll(SolverSearchState state) {
        PidokuRules rules = state.getRules();
        List<Move> moves = new ArrayList<>();
        for (int digit : rules.getUniqueDigits()) {
            if (rules.getDigitFrequency(digit) != 1) {
                continue;
            }
            find(state, digit, UnitType.ROW, UnitType.COLUMN, moves);
            find(state, digit, UnitType.COLUMN, UnitType.ROW, moves);
        }
        return moves;
    }

    /**
     * X-Wings with their base lines of {@code baseType}, clearing the digit from the two lines of
     * {@code coverType} through their corners.
     */
    private void find(SolverSearchState state, int digit, UnitType baseType, UnitType coverType, List<Move> moves) {
        PidokuRules rules = state.getRules();
        int size = state.getSize();
        List<Base> bases = new ArrayList<>();
        for (Unit line : Unit.all(baseType, rules)) {
            if (SearchUtils.remaining(state, line, digit) != 1) continue;
            Base base = twoSpots(state, line, digit);
            if (base != null) bases.add(base);
        }

        for (int i = 0; i < bases.size(); i++) {
            Base a = bases.get(i);
            for (int j = i + 1; j < bases.size(); j++) {
                Base b = bases.get(j);
                if (a.first() != b.first() || a.second() != b.second()) continue;

                Unit cover1 = new Unit(coverType, a.first());
                Unit cover2 = new Unit(coverType, a.second());
                List<Move.Candidate> eliminations = new ArrayList<>();
                // Along a cover line, cell k is where it crosses base line k.
                for (int k = 0; k < size; k++) {
                    if (k == a.line().index() || k == b.line().index()) continue;
                    addIfCandidate(state, cover1, k, digit, eliminations);
                    addIfCandidate(state, cover2, k, digit, eliminations);
                }
                Move move = Move.eliminating(eliminations);
                // Reason: the two base lines, then the two lines cleared, and the four corners.
                if (move != null) moves.add(move.because(
                        List.of(a.line(), b.line(), cover1, cover2),
                        List.of(a.line().cell(a.first(), rules), a.line().cell(a.second(), rules),
                                b.line().cell(a.first(), rules), b.line().cell(a.second(), rules)),
                        List.of(digit)));
            }
        }
    }

    /** The line, if it has exactly two spots left for the digit; otherwise null. */
    private static Base twoSpots(SolverSearchState state, Unit line, int digit) {
        PidokuRules rules = state.getRules();
        int first = -1;
        int second = -1;
        for (int k = 0; k < state.getSize(); k++) {
            if (!SearchUtils.isCandidate(state, line.rowOf(k, rules), line.colOf(k, rules), digit)) continue;
            if (first < 0) {
                first = k;
            } else if (second < 0) {
                second = k;
            } else {
                return null;
            }
        }
        return second < 0 ? null : new Base(line, first, second);
    }

    private static void addIfCandidate(SolverSearchState state, Unit unit, int k, int digit, List<Move.Candidate> out) {
        PidokuRules rules = state.getRules();
        int row = unit.rowOf(k, rules);
        int col = unit.colOf(k, rules);
        if (SearchUtils.isCandidate(state, row, col, digit)) {
            out.add(new Move.Candidate(new BoardPosition(row, col), digit));
        }
    }

    /** A base line and its two spots for the digit, as cell numbers along it ({@code first < second}). */
    private record Base(Unit line, int first, int second) {
    }
}
