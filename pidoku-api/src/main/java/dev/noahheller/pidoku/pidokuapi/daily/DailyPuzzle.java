package dev.noahheller.pidoku.pidokuapi.daily;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import dev.noahheller.pidoku.pidokuapi.puzzle.Puzzle;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;

/**
 * The puzzle in one {@link DailySlot} (table {@code daily_puzzles}, created in V2).
 *
 * <p>The id is the slot, which we assign ourselves. Implementing {@link Persistable} makes
 * {@code save} insert a new daily instead of merging it, so filling a slot that already has a
 * puzzle fails on the primary key rather than silently keeping the old one.
 */
@Entity
@Table(name = "daily_puzzles")
public class DailyPuzzle implements Persistable<DailySlot> {

    @EmbeddedId
    private DailySlot slot;

    // The difficulty column belongs to the slot; the database checks it matches the puzzle's
    // (see the foreign key in V2), so only puzzle_id is mapped here.
    @Getter
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "puzzle_id", nullable = false, updatable = false, unique = true)
    private Puzzle puzzle;

    @Transient
    private boolean isNew = true;

    /** For JPA. */
    protected DailyPuzzle() {
    }

    /** @throws IllegalArgumentException if the puzzle's difficulty isn't the slot's */
    public DailyPuzzle(DailySlot slot, Puzzle puzzle) {
        if (puzzle.getDifficulty() != slot.difficulty()) {
            throw new IllegalArgumentException("a " + puzzle.getDifficulty().id() + " puzzle can't fill the "
                    + slot.difficulty().id() + " daily slot");
        }
        this.slot = slot;
        this.puzzle = puzzle;
    }

    /** The slot this puzzle fills. */
    @Override
    public DailySlot getId() {
        return slot;
    }

    public LocalDate getDate() {
        return slot.date();
    }

    public Difficulty getDifficulty() {
        return slot.difficulty();
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }
}
