package dev.noahheller.pidoku.pidokuapi.attempt;

/**
 * Time added to a solve on the leaderboards. Every solve still counts (and keeps the streak); an
 * assisted one is just slower.
 *
 * <p>Two copies have to change with this class: {@code attempts.penalty_ms}, the generated column
 * that adds up a solve's penalties for the leaderboards to sort by (V12), and the frontend's
 * {@code game/penalties.ts}, which shows them during play.
 */
public final class Penalties {

    /** Mistakes cost this times their count squared. */
    private static final long MISTAKE_UNIT_MS = 5_000;

    /**
     * Per hint step, the first time it's revealed: where to look, what to look for, the answer. A
     * hint that places a digit costs 10 + 20 + 60 = 90 s in full. A move that only clears
     * candidates is shown in full in one tap for the first step's 10 s, and the digit it leads to
     * starts at step 2, so k eliminations and then a digit cost 10k + 20 + 60 s (see
     * {@link Attempt#revealHint}). Asking again for a step already shown costs nothing.
     */
    private static final long[] HINT_STEP_MS = {10_000, 20_000, 60_000};

    public static final int HINT_STEPS = HINT_STEP_MS.length;

    private Penalties() {
    }

    /**
     * The penalty for {@code count} wrong digits: 5 s x count², so 5 s, 20 s, 45 s… Each mistake
     * costs more than the last, so one slip is cheap but tapping digits until one fits isn't.
     */
    public static long mistakes(int count) {
        return MISTAKE_UNIT_MS * count * count;
    }

    /** @param step 1 to {@link #HINT_STEPS} */
    public static long hintStep(int step) {
        return HINT_STEP_MS[step - 1];
    }
}
