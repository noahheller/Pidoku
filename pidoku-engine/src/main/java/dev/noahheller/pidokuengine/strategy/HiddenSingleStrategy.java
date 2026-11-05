package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.List;

public class HiddenSingleStrategy extends AbstractUnitStrategy {
    @Override
    protected StrategyResult processUnit(SolverSearchState state, PidokuRules rules, List<BoardPosition> cells, UnitType type, int index) {
        for (int digit : rules.getUniqueDigits()) {
            int remaining = getRemainingFrequency(state, rules, digit, type, index);
            if (remaining <= 0) continue;

            List<BoardPosition> positions = new ArrayList<>();
            for (BoardPosition p : cells) {
                if (SearchUtils.isCandidate(state, p.getRow(), p.getCol(), digit)) {
                    positions.add(p);
                }
            }

            // Hidden Single Rule in Pidoku: If the number of matching candidate spots
            // equals exactly the remaining target frequency of that digit for this unit,
            // those spots must all be that digit.
            if (positions.size() == remaining) {
                if (remaining == 1) {
                    BoardPosition p = positions.get(0);
                    state.place(p.getRow(), p.getCol(), digit);
                    return StrategyResult.placed(p, digit);
                }

                int eliminations = 0;
                for (BoardPosition p : positions) {
                    List<Integer> cands = SearchUtils.getCandidates(state, p.getRow(), p.getCol());
                    for (int v : cands) {
                        if (v != digit) {
                            state.eliminate(p.getRow(), p.getCol(), v);
                            eliminations++;
                        }
                    }
                }
                if (eliminations > 0) {
                    return StrategyResult.eliminated(eliminations);
                }
            }
        }
        return StrategyResult.FAILED;
    }}
