package dev.noahheller.pidoku.pidokuapi.daily;

import dev.noahheller.pidoku.pidokuapi.puzzle.Difficulty;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One daily puzzle's place: a UTC date and a difficulty, e.g. the medium daily for 2026-11-10.
 * It's the primary key of {@code daily_puzzles}, so each slot holds at most one puzzle.
 */
@Embeddable
public record DailySlot(
        @Column(name = "puzzle_date", nullable = false, updatable = false)
        LocalDate date,

        @Enumerated(EnumType.STRING)
        @Column(name = "difficulty", nullable = false, updatable = false, length = 16)
        Difficulty difficulty
) implements Serializable {

    public DailySlot {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(difficulty, "difficulty");
    }
}
