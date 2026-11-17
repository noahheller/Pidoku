package dev.noahheller.pidoku.pidokuapi.puzzle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface PuzzleRepository extends JpaRepository<Puzzle, Long> {

    Optional<Puzzle> findByShareCode(String shareCode);

    boolean existsByShareCode(String shareCode);

    /** The date a puzzle is the daily for, if it's a daily. */
    @Query("SELECT d.slot.date FROM DailyPuzzle d WHERE d.puzzle.id = :puzzleId")
    Optional<LocalDate> findDailyDate(@Param("puzzleId") long puzzleId);

    long countBySourceAndDifficulty(PuzzleSource source, Difficulty difficulty);

    /** A uniformly random puzzle with this source and difficulty, if there is one. */
    default Optional<Puzzle> findRandom(PuzzleSource source, Difficulty difficulty) {
        return nativeFindRandom(source.name(), difficulty.name());
    }

    /**
     * Like {@link #findRandom(PuzzleSource, Difficulty)}, but only puzzles the player has never
     * started (has no attempt at).
     */
    default Optional<Puzzle> findRandomUnseen(PuzzleSource source, Difficulty difficulty, long playerId) {
        return nativeFindRandomUnseen(source.name(), difficulty.name(), playerId);
    }

    /**
     * The most puzzles with this source and difficulty that any one player has started (has an
     * attempt at); 0 if no one has started any.
     */
    default int maxStartedByOnePlayer(PuzzleSource source, Difficulty difficulty) {
        return nativeMaxStartedByOnePlayer(source.name(), difficulty.name());
    }

    /**
     * The puzzle with this source and difficulty that the player last started longest ago, among
     * the ones they've started. For handing out a repeat once they've started them all.
     */
    default Optional<Puzzle> findLeastRecentlyStarted(PuzzleSource source, Difficulty difficulty, long playerId) {
        return nativeFindLeastRecentlyStarted(source.name(), difficulty.name(), playerId);
    }

    // The native queries below take enum names as strings; call the enum-typed methods above instead.
    //
    // ORDER BY random() reads every matching row, which is fine for pools of a few thousand
    // puzzles per difficulty. Switch to TABLESAMPLE or a random-offset query if pools get much bigger.
    @Query(value = """
            SELECT * FROM puzzles
            WHERE source = :source AND difficulty = :difficulty
            ORDER BY random()
            LIMIT 1
            """, nativeQuery = true)
    Optional<Puzzle> nativeFindRandom(@Param("source") String source, @Param("difficulty") String difficulty);

    @Query(value = """
            SELECT * FROM puzzles p
            WHERE p.source = :source AND p.difficulty = :difficulty
              AND NOT EXISTS (SELECT 1 FROM attempts a WHERE a.player_id = :playerId AND a.puzzle_id = p.id)
            ORDER BY random()
            LIMIT 1
            """, nativeQuery = true)
    Optional<Puzzle> nativeFindRandomUnseen(@Param("source") String source, @Param("difficulty") String difficulty,
                                            @Param("playerId") long playerId);

    @Query(value = """
            SELECT coalesce(max(started), 0) FROM (
                SELECT count(DISTINCT a.puzzle_id) AS started
                FROM attempts a JOIN puzzles p ON p.id = a.puzzle_id
                WHERE p.source = :source AND p.difficulty = :difficulty
                GROUP BY a.player_id
            ) per_player
            """, nativeQuery = true)
    int nativeMaxStartedByOnePlayer(@Param("source") String source, @Param("difficulty") String difficulty);

    @Query(value = """
            SELECT p.* FROM puzzles p
            JOIN attempts a ON a.puzzle_id = p.id AND a.player_id = :playerId
            WHERE p.source = :source AND p.difficulty = :difficulty
            GROUP BY p.id
            ORDER BY max(a.started_at), p.id
            LIMIT 1
            """, nativeQuery = true)
    Optional<Puzzle> nativeFindLeastRecentlyStarted(@Param("source") String source, @Param("difficulty") String difficulty,
                                                    @Param("playerId") long playerId);
}
