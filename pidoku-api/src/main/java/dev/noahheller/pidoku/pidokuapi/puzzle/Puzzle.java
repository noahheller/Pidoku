package dev.noahheller.pidoku.pidokuapi.puzzle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * A stored puzzle (table {@code puzzles}, created in V1). Everything except {@link #getSource()}
 * is fixed once stored, so share codes and campaign levels keep pointing at the same grid.
 */
@Entity
@Table(name = "puzzles")
public class Puzzle {

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Column(name = "share_code", nullable = false, updatable = false, unique = true, length = 8)
    private String shareCode;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PuzzleSource source;

    @Getter
    @Column(nullable = false, updatable = false)
    private int size;

    /**
     * -- GETTER --
     * Encoded with
     * ; 0 is an empty cell.
     */
    @Getter
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String givens;

    /**
     * -- GETTER --
     * Encoded with
     * .
     */
    @Getter
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String solution;

    @Getter
    @Column(name = "clue_count", nullable = false, updatable = false)
    private int clueCount;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Difficulty difficulty;

    @Getter
    @Column(nullable = false, updatable = false)
    private int score;

    @Getter
    @Column(nullable = false, updatable = false)
    private double dependency;

    @Getter
    @Column(name = "max_step_cost", nullable = false, updatable = false)
    private double maxStepCost;

    @Getter
    @Column(name = "bottleneck_steps", nullable = false, updatable = false)
    private int bottleneckSteps;

    @Getter
    @Column(name = "hardest_strategy", nullable = false, updatable = false, length = 32)
    private String hardestStrategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strategy_uses", nullable = false, updatable = false)
    private Map<String, Integer> strategyUses;

    @Getter
    @Column(name = "engine_version", nullable = false, updatable = false, length = 32)
    private String engineVersion;

    @Getter
    @Column(nullable = false, updatable = false)
    private long seed;

    @Getter
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA. */
    protected Puzzle() {
    }

    public Puzzle(GeneratedPuzzle generated, String shareCode, PuzzleSource source) {
        this.shareCode = shareCode;
        this.source = source;
        this.size = generated.size();
        this.givens = generated.givens();
        this.solution = generated.solution();
        this.clueCount = generated.clueCount();
        this.difficulty = generated.difficulty();
        this.score = generated.score();
        this.dependency = generated.dependency();
        this.maxStepCost = generated.maxStepCost();
        this.bottleneckSteps = generated.bottleneckSteps();
        this.hardestStrategy = generated.hardestStrategy();
        this.strategyUses = generated.strategyUses();
        this.engineVersion = generated.engineVersion();
        this.seed = generated.seed();
    }

    /**
     * Takes this puzzle out of the random pool to become a daily puzzle.
     *
     * @throws IllegalStateException if the puzzle isn't in the pool
     */
    public void moveFromPoolToDaily() {
        if (source != PuzzleSource.POOL) {
            throw new IllegalStateException("puzzle " + id + " is " + source + ", not in the pool");
        }
        source = PuzzleSource.DAILY;
    }

    public Map<String, Integer> getStrategyUses() {
        return Map.copyOf(strategyUses);
    }

}
