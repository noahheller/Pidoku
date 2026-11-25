package dev.noahheller.pidoku.pidokuapi.attempt;

import java.util.List;

/**
 * The hint an attempt is on (column {@code attempts.last_hint}, V12).
 *
 * @param key          identifies the move being hinted, so another tap reveals its next step
 * @param step         the furthest step revealed for it, 1 to {@link Penalties#HINT_STEPS}
 * @param eliminations candidates ({@code [cell, digit]}) cleared by elimination hints already
 *                     shown in full. The board's digits can't show them, so later hints apply
 *                     them before looking for the next move.
 */
public record LastHint(String key, int step, List<int[]> eliminations) {

    public LastHint {
        eliminations = eliminations == null ? List.of() : List.copyOf(eliminations);
    }
}
