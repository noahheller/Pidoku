package dev.noahheller.pidoku.pidokuapi.attempt;

import dev.noahheller.pidoku.pidokuapi.player.Player;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    /**
     * Starts an attempt unless the player already has an unfinished one at this puzzle (the
     * partial unique index attempts_open_key). Safe when two requests arrive together: the second
     * waits for the first and then inserts nothing.
     */
    @Modifying
    @Query(value = """
            INSERT INTO attempts (player_id, puzzle_id, started_at)
            VALUES (:playerId, :puzzleId, :startedAt)
            ON CONFLICT (player_id, puzzle_id) WHERE completed_at IS NULL DO NOTHING
            """, nativeQuery = true)
    void insertIfNoneOpen(@Param("playerId") long playerId, @Param("puzzleId") long puzzleId,
                          @Param("startedAt") Instant startedAt);

    /** The player's unfinished attempt at a puzzle, if there is one. */
    @Query("SELECT a FROM Attempt a WHERE a.player = :player AND a.puzzle.id = :puzzleId AND a.completedAt IS NULL")
    Optional<Attempt> findOpen(@Param("player") Player player, @Param("puzzleId") long puzzleId);

    /**
     * One of the player's attempts with its puzzle, row-locked for the rest of the transaction so
     * two requests completing it at once don't both record a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Attempt a JOIN FETCH a.puzzle WHERE a.id = :id AND a.player = :player")
    Optional<Attempt> findForUpdate(@Param("id") long id, @Param("player") Player player);
}
