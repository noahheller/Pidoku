package dev.noahheller.pidoku.pidokuapi.daily;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface DailyPuzzleRepository extends JpaRepository<DailyPuzzle, DailySlot> {

    /** Every daily for a date, with its puzzle loaded so it can be used outside the transaction. */
    @Query("SELECT d FROM DailyPuzzle d JOIN FETCH d.puzzle WHERE d.slot.date = :date")
    List<DailyPuzzle> findAllWithPuzzle(@Param("date") LocalDate date);

    /** Slots from {@code from} to {@code to} (both inclusive) that already have a puzzle. */
    @Query("SELECT d.slot FROM DailyPuzzle d WHERE d.slot.date BETWEEN :from AND :to")
    Set<DailySlot> findFilledSlots(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
