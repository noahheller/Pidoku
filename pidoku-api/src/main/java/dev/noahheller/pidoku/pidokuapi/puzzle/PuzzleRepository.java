package dev.noahheller.pidoku.pidokuapi.puzzle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PuzzleRepository extends JpaRepository<Puzzle, Long> {

    Optional<Puzzle> findByShareCode(String shareCode);

    boolean existsByShareCode(String shareCode);

    long countBySourceAndDifficulty(PuzzleSource source, Difficulty difficulty);

    /** A uniformly random puzzle with this source and difficulty, if there is one. */
    default Optional<Puzzle> findRandom(PuzzleSource source, Difficulty difficulty) {
        return nativeFindRandom(source.name(), difficulty.name());
    }

    /**
     * Like {@link #findRandom(PuzzleSource, Difficulty)}, but row-locks the puzzle for the rest of
     * the transaction and skips puzzles another transaction has locked. Use it to take a puzzle out
     * of the pool without two callers taking the same one.
     */
    default Optional<Puzzle> findRandomForUpdate(PuzzleSource source, Difficulty difficulty) {
        return nativeFindRandomForUpdate(source.name(), difficulty.name());
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
            SELECT * FROM puzzles
            WHERE source = :source AND difficulty = :difficulty
            ORDER BY random()
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Puzzle> nativeFindRandomForUpdate(@Param("source") String source, @Param("difficulty") String difficulty);
}
