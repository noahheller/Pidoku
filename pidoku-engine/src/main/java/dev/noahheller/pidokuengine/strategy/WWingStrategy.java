package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WWingStrategy implements Strategy {
    @Override
    public List<Move> findAll(SolverSearchState state) {
        PidokuRules rules = state.getRules();
        List<Move> moves = new ArrayList<>();
        List<BivalueCell> bivalueCells = findBivalueCells(state);
        if (bivalueCells.size() < 2) {
            return moves;
        }

        Map<Integer, List<StrongLink>> strongLinksByDigit = buildStrongLinks(state);

        for (int i = 0; i < bivalueCells.size(); i++) {
            BivalueCell a = bivalueCells.get(i);
            for (int j = i + 1; j < bivalueCells.size(); j++) {
                BivalueCell b = bivalueCells.get(j);
                if (!a.hasSamePair(b)) continue;

                int d1 = a.first();
                int d2 = a.second();
                findWings(state, rules, strongLinksByDigit, a, b, d1, d2, moves);
                findWings(state, rules, strongLinksByDigit, a, b, d2, d1, moves);
            }
        }

        return moves;
    }

    private void findWings(
            SolverSearchState state,
            PidokuRules rules,
            Map<Integer, List<StrongLink>> strongLinksByDigit,
            BivalueCell a,
            BivalueCell b,
            int linkDigit,
            int eliminateDigit,
            List<Move> moves
    ) {
        if (rules.getDigitFrequency(linkDigit) != 1) return;
        if (rules.getDigitFrequency(eliminateDigit) != 1) return;

        // Only digits with links are in the map.
        List<StrongLink> links = strongLinksByDigit.get(linkDigit);
        if (links == null) return;

        for (StrongLink link : links) {
            if (!connects(a.position(), b.position(), link, rules)) continue;

            List<Move.Candidate> eliminations = new ArrayList<>();
            int size = state.getSize();
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    if (!state.isEmpty(row, col)) continue;
                    BoardPosition pos = new BoardPosition(row, col);
                    if (pos.equals(a.position()) || pos.equals(b.position())) continue;
                    if (!sees(rules, pos, a.position()) || !sees(rules, pos, b.position())) continue;
                    if (SearchUtils.isCandidate(state, pos, eliminateDigit)) {
                        eliminations.add(new Move.Candidate(pos, eliminateDigit));
                    }
                }
            }
            Move move = Move.eliminating(eliminations);
            // Reason: the unit holding the strong link; the two wing cells, then the link's ends;
            // the link digit, then the digit cleared.
            if (move != null) moves.add(move.because(List.of(link.unit()),
                    List.of(a.position(), b.position(), link.first(), link.second()), List.of(linkDigit, eliminateDigit)));
        }
    }

    private boolean connects(BoardPosition a, BoardPosition b, StrongLink link, PidokuRules rules) {
        // link must be between two *other* cells.
        if (link.first().equals(a) || link.first().equals(b) || link.second().equals(a) || link.second().equals(b)) {
            return false;
        }
        return (sees(rules, a, link.first()) && sees(rules, b, link.second()))
                || (sees(rules, a, link.second()) && sees(rules, b, link.first()));
    }

    private boolean sees(PidokuRules rules, BoardPosition a, BoardPosition b) {
        if (a.getRow() == b.getRow()) return true;
        if (a.getCol() == b.getCol()) return true;
        return rules.boxIndex(a.getRow(), a.getCol()) == rules.boxIndex(b.getRow(), b.getCol());
    }

    private List<BivalueCell> findBivalueCells(SolverSearchState state) {
        int size = state.getSize();
        List<BivalueCell> bivalueCells = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                int mask = state.candidateMask(row, col);
                if (Integer.bitCount(mask) == 2) {
                    // Lowest bit first, so the pair's digits are in ascending order.
                    int a = Integer.numberOfTrailingZeros(mask);
                    int b = Integer.numberOfTrailingZeros(mask & ~(1 << a));
                    bivalueCells.add(new BivalueCell(new BoardPosition(row, col), a, b));
                }
            }
        }
        return bivalueCells;
    }

    /**
     * For each digit π has once, every unit with only two spots left for it, in the order rows,
     * columns, boxes. Digits with none are left out.
     */
    private Map<Integer, List<StrongLink>> buildStrongLinks(SolverSearchState state) {
        PidokuRules rules = state.getRules();
        Map<Integer, List<StrongLink>> linksByDigit = new HashMap<>();
        for (int digit : rules.getUniqueDigits()) {
            if (rules.getDigitFrequency(digit) != 1) continue;
            List<StrongLink> links = new ArrayList<>();
            for (UnitType type : UnitType.values()) {
                for (Unit unit : Unit.all(type, rules)) {
                    List<BoardPosition> spots = SearchUtils.candidateCells(state, unit, digit);
                    if (spots.size() == 2) {
                        links.add(new StrongLink(spots.get(0), spots.get(1), unit));
                    }
                }
            }
            if (!links.isEmpty()) {
                linksByDigit.put(digit, links);
            }
        }
        return linksByDigit;
    }

    /** A cell with only two candidates, {@code first < second}. */
    private record BivalueCell(BoardPosition position, int first, int second) {

        boolean hasSamePair(BivalueCell other) {
            return first == other.first && second == other.second;
        }
    }

    /** The only two places left for a digit in {@code unit}. */
    private record StrongLink(BoardPosition first, BoardPosition second, Unit unit) {
    }
}
