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

    /** Null while the attempt is unfinished. */
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

    /** @throws IllegalStateException if the attempt is already complete */
    public void complete(Instant now, int mistakes) {
        if (isCompleted()) {
            throw new IllegalStateException("attempt " + id + " is already complete");
        }
        this.completedAt = now;
        // Never negative, even if the server's clock stepped back in between.
        this.elapsedMs = Math.max(0, Duration.between(startedAt, now).toMillis());
        this.mistakes = mistakes;
    }
}
