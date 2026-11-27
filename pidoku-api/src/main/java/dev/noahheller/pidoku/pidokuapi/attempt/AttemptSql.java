package dev.noahheller.pidoku.pidokuapi.attempt;

/**
 * SQL expressions over an {@code attempts} row aliased {@code a}, shared by every query that times
 * solves (leaderboards, stats, profiles, history, recent puzzles), so they all count the same way.
 * Splice them into a query with {@link String#formatted}.
 */
public final class AttemptSql {

    /**
     * The game's own time: {@code active_ms}, which pauses while the tab is hidden, or the server's
     * wall-clock {@code elapsed_ms} for attempts recorded before the game sent one.
     */
    public static final String GAME_MS = "COALESCE(a.active_ms, a.elapsed_ms)";

    /** What the leaderboards rank by: {@link #GAME_MS} plus {@code penalty_ms} (see {@link Penalties}). */
    public static final String RANKED_MS = GAME_MS + " + a.penalty_ms";

    private AttemptSql() {
    }
}
