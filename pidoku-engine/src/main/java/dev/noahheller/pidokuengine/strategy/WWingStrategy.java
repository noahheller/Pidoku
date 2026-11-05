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
    public StrategyResult apply(SolverSearchState state) {
        PidokuRules rules = state.getRules();
        List<BivalueCell> bivalueCells = findBivalueCells(state);
        if (bivalueCells.size() < 2) {
            return StrategyResult.FAILED;
        }

        Map<Integer, List<StrongLink>> strongLinksByDigit = buildStrongLinks(state);

        for (int i = 0; i < bivalueCells.size(); i++) {
            BivalueCell a = bivalueCells.get(i);
            for (int j = i + 1; j < bivalueCells.size(); j++) {
                BivalueCell b = bivalueCells.get(j);
                if (!a.hasSamePair(b)) continue;

                int d1 = a.first;
                int d2 = a.second;
                StrategyResult res = tryWing(state, rules, strongLinksByDigit, a, b, d1, d2);
                if (res.madeProgress()) return res;
                res = tryWing(state, rules, strongLinksByDigit, a, b, d2, d1);
                if (res.madeProgress()) return res;
            }
        }

        return StrategyResult.FAILED;
    }

    private StrategyResult tryWing(
            SolverSearchState state,
            PidokuRules rules,
            Map<Integer, List<StrongLink>> strongLinksByDigit,
            BivalueCell a,
            BivalueCell b,
            int linkDigit,
            int eliminateDigit
    ) {
        if (rules.getDigitFrequency(linkDigit) != 1) return StrategyResult.FAILED;
        if (rules.getDigitFrequency(eliminateDigit) != 1) return StrategyResult.FAILED;

        List<StrongLink> links = strongLinksByDigit.get(linkDigit);
        if (links == null || links.isEmpty()) return StrategyResult.FAILED;

        for (StrongLink link : links) {
            if (!connects(a.position, b.position, link, rules)) continue;

            int eliminations = 0;
            int size = state.getSize();
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    if (!state.isEmpty(row, col)) continue;
                    if (a.position.getRow() == row && a.position.getCol() == col) continue;
                    if (b.position.getRow() == row && b.position.getCol() == col) continue;
                    BoardPosition pos = new BoardPosition(row, col);
                    if (!sees(rules, pos, a.position) || !sees(rules, pos, b.position)) continue;
                    if (SearchUtils.isCandidate(state, pos, eliminateDigit)) {
                        state.eliminate(row, col, eliminateDigit);
                        eliminations++;
                    }
                }
            }
            if (eliminations > 0) return StrategyResult.eliminated(eliminations);
        }

        return StrategyResult.FAILED;
    }

    private boolean connects(BoardPosition a, BoardPosition b, StrongLink link, PidokuRules rules) {
        return (sees(rules, a, link.first) && sees(rules, b, link.second))
                || (sees(rules, a, link.second) && sees(rules, b, link.first));
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
                if (!state.isEmpty(row, col)) continue;
                List<Integer> candidates = SearchUtils.getCandidates(state, row, col);
                if (candidates.size() == 2) {
                    int a = candidates.get(0);
                    int b = candidates.get(1);
                    if (a > b) {
                        int tmp = a;
                        a = b;
                        b = tmp;
                    }
                    bivalueCells.add(new BivalueCell(new BoardPosition(row, col), a, b));
                }
            }
        }
        return bivalueCells;
    }

    private Map<Integer, List<StrongLink>> buildStrongLinks(SolverSearchState state) {
        PidokuRules rules = state.getRules();
        Map<Integer, List<StrongLink>> linksByDigit = new HashMap<>();
        int[] digits = rules.getUniqueDigits();
        for (int digit : digits) {
            if (rules.getDigitFrequency(digit) != 1) continue;
            List<StrongLink> links = new ArrayList<>();
            addRowLinks(state, digit, links);
            addColLinks(state, digit, links);
            addBoxLinks(state, digit, links);
            if (!links.isEmpty()) {
                linksByDigit.put(digit, links);
            }
        }
        return linksByDigit;
    }

    private void addRowLinks(SolverSearchState state, int digit, List<StrongLink> links) {
        int size = state.getSize();
        for (int row = 0; row < size; row++) {
            List<BoardPosition> candidates = new ArrayList<>();
            for (int col = 0; col < size; col++) {
                if (SearchUtils.isCandidate(state, row, col, digit)) {
                    candidates.add(new BoardPosition(row, col));
                }
            }
            if (candidates.size() == 2) {
                links.add(new StrongLink(candidates.get(0), candidates.get(1)));
            }
        }
    }

    private void addColLinks(SolverSearchState state, int digit, List<StrongLink> links) {
        int size = state.getSize();
        for (int col = 0; col < size; col++) {
            List<BoardPosition> candidates = new ArrayList<>();
            for (int row = 0; row < size; row++) {
                if (SearchUtils.isCandidate(state, row, col, digit)) {
                    candidates.add(new BoardPosition(row, col));
                }
            }
            if (candidates.size() == 2) {
                links.add(new StrongLink(candidates.get(0), candidates.get(1)));
            }
        }
    }

    private void addBoxLinks(SolverSearchState state, int digit, List<StrongLink> links) {
        int totalBoxes = state.getRules().totalBoxes(state.getSize());
        for (int box = 0; box < totalBoxes; box++) {
            List<BoardPosition> candidates = SearchUtils.emptyCellsInBox(state, box);
            List<BoardPosition> digitCandidates = new ArrayList<>();
            for (BoardPosition pos : candidates) {
                if (SearchUtils.isCandidate(state, pos, digit)) {
                    digitCandidates.add(pos);
                }
            }
            if (digitCandidates.size() == 2) {
                links.add(new StrongLink(digitCandidates.get(0), digitCandidates.get(1)));
            }
        }
    }

    private static class BivalueCell {
        private final BoardPosition position;
        private final int first;
        private final int second;

        private BivalueCell(BoardPosition position, int first, int second) {
            this.position = position;
            this.first = first;
            this.second = second;
        }

        private boolean hasSamePair(BivalueCell other) {
            return this.first == other.first && this.second == other.second;
        }
    }

    private static class StrongLink {
        private final BoardPosition first;
        private final BoardPosition second;

        private StrongLink(BoardPosition first, BoardPosition second) {
            this.first = first;
            this.second = second;
        }
    }
}
