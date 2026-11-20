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

import java.time.Duration;
import java.time.Instant;

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

    @Column(name = "hints_used", nullable = false)
    private int hintsUsed;

    /**
     * The client's own timer (paused while the tab was hidden), for calibration. Null while
     * unfinished or if the client didn't send it.
     */
    @Column(name = "active_ms")
    private Long activeMs;

    /** The completion arrived some time after the solve, and the times were moved back by that delay. */
    @Column(nullable = false)
    private boolean late;

    /** For JPA. */
    protected Attempt() {
    }

    public boolean isCompleted() {
        return completedAt != null;
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
     * @throws IllegalStateException if the attempt is already complete
     */
    public void complete(Instant now, int mistakes, Long activeMs, Duration lateBy) {
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
    }
}
