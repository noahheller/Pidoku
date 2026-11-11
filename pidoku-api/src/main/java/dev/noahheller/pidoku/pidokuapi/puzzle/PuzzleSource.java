package dev.noahheller.pidoku.pidokuapi.puzzle;

/**
 * What a stored puzzle is used for. Each puzzle has exactly one use, so the random endpoint
 * (which only serves {@link #POOL}) can never hand out a daily, a campaign level or a
 * player's custom puzzle.
 */
public enum PuzzleSource {
    /** Available to {@code GET /api/puzzles/random}. */
    POOL,
    /** Assigned to a date in {@code daily_puzzles}. */
    DAILY,
    /** A campaign level. */
    CAMPAIGN,
    /** Generated for one player's custom difficulty. */
    CUSTOM
}
