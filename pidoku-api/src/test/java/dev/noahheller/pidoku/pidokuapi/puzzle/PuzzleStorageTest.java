package dev.noahheller.pidoku.pidokuapi.puzzle;

import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import dev.noahheller.pidoku.pidokuapi.support.TestPuzzles;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Storing puzzles through {@link PuzzleService}, and the constraints in V1 that guard the table. */
class PuzzleStorageTest extends IntegrationTest {

    @Autowired
    PuzzleService puzzleService;

    @Autowired
    PuzzleRepository puzzles;

    @Test
    void storedPuzzleKeepsEveryField() {
        GeneratedPuzzle generated = TestPuzzles.generated(Difficulty.MEDIUM, 1234L);
        long id = puzzleService.store(generated, PuzzleSource.POOL).getId();

        Puzzle stored = puzzles.findById(id).orElseThrow();
        assertThat(stored.getShareCode()).hasSize(ShareCodeGenerator.LENGTH);
        assertThat(stored.getSource()).isEqualTo(PuzzleSource.POOL);
        assertThat(stored.getSize()).isEqualTo(9);
        assertThat(stored.getGivens()).isEqualTo(TestPuzzles.GIVENS);
        assertThat(stored.getSolution()).isEqualTo(TestPuzzles.SOLUTION);
        assertThat(stored.getClueCount()).isEqualTo(TestPuzzles.CLUE_COUNT);
        assertThat(stored.getDifficulty()).isEqualTo(Difficulty.MEDIUM);
        assertThat(stored.getScore()).isEqualTo(generated.score());
        assertThat(stored.getDependency()).isEqualTo(generated.dependency());
        assertThat(stored.getMaxStepCost()).isEqualTo(generated.maxStepCost());
        assertThat(stored.getBottleneckSteps()).isEqualTo(generated.bottleneckSteps());
        assertThat(stored.getHardestStrategy()).isEqualTo("INTERSECTION_REMOVAL");
        assertThat(stored.getStrategyUses()).isEqualTo(generated.strategyUses());
        assertThat(stored.getEngineVersion()).isEqualTo("test");
        assertThat(stored.getSeed()).isEqualTo(1234L);
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    @Test
    void strategyUsesIsStoredAsAJsonObject() {
        puzzleService.store(TestPuzzles.generated(Difficulty.MEDIUM), PuzzleSource.POOL);

        Integer nakedPairs = jdbc.queryForObject(
                "SELECT (strategy_uses ->> 'NAKED_PAIR')::int FROM puzzles", Integer.class);
        assertThat(nakedPairs).isEqualTo(2);
    }

    @Test
    void everyPuzzleGetsItsOwnShareCode() {
        String first = puzzleService.store(TestPuzzles.generated(Difficulty.EASY), PuzzleSource.POOL).getShareCode();
        String second = puzzleService.store(TestPuzzles.generated(Difficulty.EASY), PuzzleSource.POOL).getShareCode();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void databaseRejectsAGridOfTheWrongLength() {
        assertThatThrownBy(() -> insertRaw("POOL", "MEDIUM", TestPuzzles.GIVENS.substring(1)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("puzzles_givens_check");
    }

    @Test
    void databaseRejectsAnUnknownSourceOrDifficulty() {
        assertThatThrownBy(() -> insertRaw("SOMEWHERE", "MEDIUM", TestPuzzles.GIVENS))
                .hasMessageContaining("puzzles_source_check");
        assertThatThrownBy(() -> insertRaw("POOL", "IMPOSSIBLE", TestPuzzles.GIVENS))
                .hasMessageContaining("puzzles_difficulty_check");
    }

    @Test
    void databaseAcceptsEveryDifficultyAndSourceTheCodeKnows() {
        for (Difficulty difficulty : Difficulty.values()) {
            for (PuzzleSource source : PuzzleSource.values()) {
                puzzleService.store(TestPuzzles.generated(difficulty), source);
            }
        }
        assertThat(puzzles.count()).isEqualTo((long) Difficulty.values().length * PuzzleSource.values().length);
    }

    @Test
    void storedPuzzlesAreReturnedWithAnUnmodifiableStrategyMap() {
        Puzzle stored = puzzleService.store(TestPuzzles.generated(Difficulty.EASY), PuzzleSource.POOL);
        Map<String, Integer> uses = stored.getStrategyUses();
        assertThatThrownBy(() -> uses.put("W_WING", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    private void insertRaw(String source, String difficulty, String givens) {
        jdbc.update("""
                INSERT INTO puzzles (share_code, source, size, givens, solution, clue_count, difficulty, score,
                                     dependency, max_step_cost, bottleneck_steps, hardest_strategy, strategy_uses,
                                     engine_version, seed)
                VALUES ('RAWCODE2', ?, 9, ?, ?, 31, ?, 92, 4.0, 6.0, 9, 'NAKED_PAIR', '{}'::jsonb, 'test', 1)
                """, source, givens, TestPuzzles.SOLUTION, difficulty);
    }
}
