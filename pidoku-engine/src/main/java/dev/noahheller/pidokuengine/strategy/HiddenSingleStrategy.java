package dev.noahheller.pidokuengine.strategy;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SearchUtils;
import dev.noahheller.pidokuengine.searching.SolverSearchState;

import java.util.ArrayList;
import java.util.List;

/**
 * A digit that has exactly as many possible spots left in a unit as copies it
 * still needs there. Every one of those spots must be that digit. For a digit that
 * appears once this is the classic hidden single; for 1 and 5 it can be two spots.
 * Split by unit type because scanning a box is easier for people than scanning a line.
 */
public class HiddenSingleStrategy extends AbstractUnitStrategy {

    public HiddenSingleStrategy(UnitType... unitTypes) {
        super(unitTypes);
    }

    @Override
    protected void processUnit(SolverSearchState state, PidokuRules rules, List<BoardPosition> cells,
                               UnitType type, int index, List<Move> out) {
        for (int digit : rules.getUniqueDigits()) {
            int remaining = getRemainingFrequency(state, rules, digit, type, index);
            if (remaining <= 0) continue;

            List<BoardPosition> positions = new ArrayList<>();
            for (BoardPosition p : cells) {
                if (SearchUtils.isCandidate(state, p.getRow(), p.getCol(), digit)) {
                    positions.add(p);
                }
            }

            if (positions.size() == remaining) {
                for (BoardPosition p : positions) {
                    out.add(Move.placement(p, digit));
                }
            }
        }
    }
}
