package dev.noahheller.pidoku.pidokuapi.attempt;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One hint step, in the shape the tutorial's explained steps use (cells as row-major indices).
 * Only what the step reveals is filled in, so the answer can't be read from an earlier step:
 * <ol>
 *   <li>where: {@code units}</li>
 *   <li>what to look for: adds {@code strategy}, {@code pattern} and {@code blockers}</li>
 *   <li>the answer: adds {@code cell}, {@code digit}, {@code eliminations} and {@code text}</li>
 * </ol>
 *
 * @param cell          the cell to fill, or null for a step that only clears candidates
 * @param hintsUsed     the attempt's hint steps so far, this one included
 * @param hintPenaltyMs the attempt's time added for hints so far
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HintResponse(
        int step,
        List<UnitRef> units,
        String strategy,
        List<Integer> pattern,
        List<Integer> blockers,
        Integer cell,
        Integer digit,
        List<CandidateRef> eliminations,
        String text,
        int hintsUsed,
        long hintPenaltyMs
) {

    /** @param type row, column or box */
    public record UnitRef(String type, int index) {
    }

    public record CandidateRef(int cell, int digit) {
    }
}
