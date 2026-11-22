package dev.noahheller.pidokuengine.explain;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.strategy.Move;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.Unit;

import java.util.List;

/**
 * A solving step as a player can follow it: what to look at and one plain sentence saying why it
 * works. Made by {@link MoveExplainer}; the tutorial and hints show it on the board.
 *
 * <p>The sentence never names the technique: the frontend has the player-facing names
 * ({@code strategies.ts}), so there's one source for them.
 *
 * @param strategy     the technique, as in {@code PidokuPuzzle}'s hardest strategy
 * @param cell         the cell a digit goes in, or null for a step that only clears candidates
 * @param digit        the digit placed, or 0 for a step that only clears candidates
 * @param eliminations the candidates the step clears; empty for a placement
 * @param units        the rows, columns and boxes the reasoning is about, to shade lightly
 * @param pattern      the cells that make the pattern (for a single, every spot left for the
 *                     digit), to mark strongly
 * @param blockers     filled cells that rule the digit out of the unit's other cells, for the
 *                     singles; empty for the other techniques
 * @param text         one sentence, e.g. "Row 4 already has one 5 and needs its second, and this
 *                     is the only cell left in it that can take a 5."
 */
public record Explanation(
        StrategyEnum strategy,
        BoardPosition cell,
        int digit,
        List<Move.Candidate> eliminations,
        List<Unit> units,
        List<BoardPosition> pattern,
        List<BoardPosition> blockers,
        String text
) {

    public Explanation {
        eliminations = List.copyOf(eliminations);
        units = List.copyOf(units);
        pattern = List.copyOf(pattern);
        blockers = List.copyOf(blockers);
    }

    public boolean isPlacement() {
        return cell != null;
    }
}
