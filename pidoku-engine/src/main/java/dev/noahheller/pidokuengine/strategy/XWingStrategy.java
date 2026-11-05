package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.List;

public class XWingStrategy implements Strategy {
    @Override
    public StrategyResult apply(SolverSearchState state) {
        PidokuRules rules = state.getRules();
        int[] digits = rules.getUniqueDigits();
        for (int digit : digits) {
            if (rules.getDigitFrequency(digit) != 1) {
                continue;
            }
            StrategyResult rowBased = applyRowBased(state, digit);
            if (rowBased.madeProgress()) return rowBased;
            StrategyResult colBased = applyColBased(state, digit);
            if (colBased.madeProgress()) return colBased;
        }
        return StrategyResult.FAILED;
    }

    private StrategyResult applyRowBased(SolverSearchState state, int digit) {
        int size = state.getSize();
        List<RowPair> rowPairs = new ArrayList<>();
        for (int row = 0; row < size; row++) {
            int remaining = state.getRules().getDigitFrequency(digit) - state.getRowDigitCount(row, digit);
            if (remaining != 1) continue;

            List<Integer> cols = new ArrayList<>();
            for (int col = 0; col < size; col++) {
                if (SearchUtils.isCandidate(state, row, col, digit)) {
                    cols.add(col);
                }
            }
            if (cols.size() == 2) {
                int c1 = cols.get(0);
                int c2 = cols.get(1);
                rowPairs.add(new RowPair(row, Math.min(c1, c2), Math.max(c1, c2)));
            }
        }

        for (int i = 0; i < rowPairs.size(); i++) {
            RowPair a = rowPairs.get(i);
            for (int j = i + 1; j < rowPairs.size(); j++) {
                RowPair b = rowPairs.get(j);
                if (a.col1 != b.col1 || a.col2 != b.col2) continue;

                int eliminations = 0;
                for (int row = 0; row < size; row++) {
                    if (row == a.row || row == b.row) continue;
                    if (SearchUtils.isCandidate(state, row, a.col1, digit)) {
                        state.eliminate(row, a.col1, digit);
                        eliminations++;
                    }
                    if (SearchUtils.isCandidate(state, row, a.col2, digit)) {
                        state.eliminate(row, a.col2, digit);
                        eliminations++;
                    }
                }
                if (eliminations > 0) return StrategyResult.eliminated(eliminations);
            }
        }

        return StrategyResult.FAILED;
    }

    private StrategyResult applyColBased(SolverSearchState state, int digit) {
        int size = state.getSize();
        List<ColPair> colPairs = new ArrayList<>();
        for (int col = 0; col < size; col++) {
            int remaining = state.getRules().getDigitFrequency(digit) - state.getColDigitCount(col, digit);
            if (remaining != 1) continue;

            List<Integer> rows = new ArrayList<>();
            for (int row = 0; row < size; row++) {
                if (SearchUtils.isCandidate(state, row, col, digit)) {
                    rows.add(row);
                }
            }
            if (rows.size() == 2) {
                int r1 = rows.get(0);
                int r2 = rows.get(1);
                colPairs.add(new ColPair(col, Math.min(r1, r2), Math.max(r1, r2)));
            }
        }

        for (int i = 0; i < colPairs.size(); i++) {
            ColPair a = colPairs.get(i);
            for (int j = i + 1; j < colPairs.size(); j++) {
                ColPair b = colPairs.get(j);
                if (a.row1 != b.row1 || a.row2 != b.row2) continue;

                int eliminations = 0;
                for (int col = 0; col < size; col++) {
                    if (col == a.col || col == b.col) continue;
                    if (SearchUtils.isCandidate(state, a.row1, col, digit)) {
                        state.eliminate(a.row1, col, digit);
                        eliminations++;
                    }
                    if (SearchUtils.isCandidate(state, a.row2, col, digit)) {
                        state.eliminate(a.row2, col, digit);
                        eliminations++;
                    }
                }
                if (eliminations > 0) return StrategyResult.eliminated(eliminations);
            }
        }

        return StrategyResult.FAILED;
    }

    private static class RowPair {
        private final int row;
        private final int col1;
        private final int col2;

        private RowPair(int row, int col1, int col2) {
            this.row = row;
            this.col1 = col1;
            this.col2 = col2;
        }
    }

    private static class ColPair {
        private final int col;
        private final int row1;
        private final int row2;

        private ColPair(int col, int row1, int row2) {
            this.col = col;
            this.row1 = row1;
            this.row2 = row2;
        }
    }
}
