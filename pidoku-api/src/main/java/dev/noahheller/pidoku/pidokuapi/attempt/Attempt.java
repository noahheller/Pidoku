package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One go at a puzzle by a player (table {@code attempts}, created in V3). Rows are inserted by
 * {@link AttemptRepository#insertIfNoneOpen} when the player starts the puzzle; JPA only reads
 * them and marks them complete.
 */
@Getter
@Entity
@Table(name = "attempts")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "puzzle_id", nullable = false, updatable = false)
    private Puzzle puzzle;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /**
     * When the puzzle was solved: when the completion arrived, or for a late one, that minus the
     * delay the client reported. Null while the attempt is unfinished.
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /** From {@code startedAt} to {@code completedAt}, both server times. Null while unfinished. */
    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    /** As reported by the client. */
    @Column(nullable = false)
    private int mistakes;

    /** Hint steps revealed. */
    @Column(name = "hints_used", nullable = false)
    private int hintsUsed;

    /** Time added for hints (see {@link Penalties}). */
    @Column(name = "hint_penalty_ms", nullable = false)
    private long hintPenaltyMs;

    /** The hint being shown, or null; see {@link LastHint}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_hint")
    private LastHint lastHint;

    /**
     * The client's own timer (paused while the tab was hidden), for calibration. Null while
     * unfinished or if the client didn't send it.
     */
    @Column(name = "active_ms")
    private Long activeMs;

    /** The completion arrived some time after the solve, and the times were moved back by that delay. */
    @Column(nullable = false)
    private boolean late;

    /** The board as the player last left it, for another device; null until the first save. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "progress")
    private AttemptProgress progress;

    /** When {@link #progress} was saved (server time); null with it. */
    @Column(name = "progress_updated_at")
    private Instant progressUpdatedAt;

    /** The player used Auto notes during the attempt. */
    @Column(name = "auto_notes_used", nullable = false)
    private boolean autoNotesUsed;

    /** For JPA. */
    protected Attempt() {
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    /**
     * What the leaderboards add to the time: the mistakes recorded so far plus the hints. The same
     * sum as the {@code penalty_ms} column (V12), which isn't read here because it's only
     * recomputed when the row is written.
     */
    public long getPenaltyMs() {
        return Penalties.mistakes(mistakes) + hintPenaltyMs;
    }

    /**
     * Records that a hint step was shown: the next step of the move hinted last time, or the
     * first step of a new one. Each step is charged once; asking again for a move already shown
     * in full charges nothing.
     *
     * @param key          identifies the move
     * @param eliminations the candidates the move clears ({@code [cell, digit]}), empty for a
     *                     placement. Once its last step is shown they're remembered, so later
     *                     hints build on them (see {@link LastHint}).
     * @return the step now revealed, 1 to {@link Penalties#HINT_STEPS}
     * @throws IllegalStateException if the attempt is already complete
     */
    public int revealHint(String key, List<int[]> eliminations) {
        if (isCompleted()) {
            throw new IllegalStateException("attempt " + id + " is already complete");
        }
        boolean sameMove = lastHint != null && lastHint.key().equals(key);
        int step = sameMove ? Math.min(lastHint.step() + 1, Penalties.HINT_STEPS) : 1;
        if (!sameMove || step > lastHint.step()) {
            hintsUsed++;
            hintPenaltyMs += Penalties.hintStep(step);
        }
        List<int[]> shown = new ArrayList<>(shownEliminations());
        if (step == Penalties.HINT_STEPS && !(sameMove && lastHint.step() == step)) {
            shown.addAll(eliminations);
        }
        lastHint = new LastHint(key, step, shown);
        return step;
    }

    /**
     * Forgets the hint being shown, so the next one starts again at step 1: a daily restarted keeps
     * its clock and penalties, but its board is wiped.
     */
    public void forgetHint() {
        this.lastHint = null;
    }

    /** The candidates cleared by elimination hints already shown in full. */
    public List<int[]> shownEliminations() {
        return lastHint == null ? List.of() : lastHint.eliminations();
    }

    /**
     * Starts the clock again from {@code now}: the player wiped the board and started over.
     *
     * @throws IllegalStateException if the attempt is already complete
     */
    public void restart(Instant now) {
        if (isCompleted()) {
            throw new IllegalStateException("attempt " + id + " is already complete");
        }
        this.startedAt = now;
        // The board was wiped, so another device shouldn't bring the old one back.
        this.progress = null;
        this.progressUpdatedAt = null;
        this.autoNotesUsed = false;
        // A fresh start is a fresh solve: hints from before don't count against it.
        this.hintsUsed = 0;
        this.hintPenaltyMs = 0;
        this.lastHint = null;
    }

    /**
     * Saves the board as the player left it, so another device can carry on from it.
     *
     * @throws IllegalStateException if the attempt is already complete
     */
    public void saveProgress(AttemptProgress progress, boolean autoNotesUsed, Instant now) {
        if (isCompleted()) {
            throw new IllegalStateException("attempt " + id + " is already complete");
        }
        this.progress = progress;
        this.progressUpdatedAt = now;
        // Once used, always used: a later save from a device that didn't press it doesn't undo that.
        this.autoNotesUsed |= autoNotesUsed;
    }

    /**
     * Records the solve.
     *
     * @param now      when the completion arrived (server time)
     * @param mistakes wrong digits entered, as counted by the client
     * @param activeMs the client's own timer, or null if it didn't send one; capped at the elapsed time
     * @param lateBy   how long before {@code now} the puzzle was really solved, as the client claims;
     *                 null or zero for a completion sent straight away. The solve is then recorded at
     *                 {@code now - lateBy}, but never before the attempt started.
     * @param autoNotesUsed the player used Auto notes (also kept if a progress save said so)
     * @throws IllegalStateException if the attempt is already complete
     */
    public void complete(Instant now, int mistakes, Long activeMs, Duration lateBy, boolean autoNotesUsed) {
        if (isCompleted()) {
            throw new IllegalStateException("attempt " + id + " is already complete");
        }
        this.late = lateBy != null && lateBy.isPositive();
        Instant solvedAt = late ? now.minus(lateBy) : now;
        // Never before the start, so elapsed_ms is never negative: not for a delay longer than the
        // attempt, and not if the server's clock stepped back in between.
        this.completedAt = solvedAt.isBefore(startedAt) ? startedAt : solvedAt;
        this.elapsedMs = Duration.between(startedAt, completedAt).toMillis();
        this.mistakes = mistakes;
        // Leaderboards rank the client's timer, so it can't claim more than the server saw pass:
        // time on screen fits inside the time since the start. (It can come out a little under
        // the real figure, by the network delay of the start request, never over.)
        this.activeMs = activeMs == null ? null : Math.min(activeMs, elapsedMs);
        this.autoNotesUsed |= autoNotesUsed;
        // The solved grid is checked, not kept: a finished attempt needs no board to carry on from.
        this.progress = null;
        this.progressUpdatedAt = null;
    }
}
