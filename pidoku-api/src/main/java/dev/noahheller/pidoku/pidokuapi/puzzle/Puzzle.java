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

/**
 * A stored puzzle (table {@code puzzles}, created in V1). Fixed once stored, so share codes and
 * campaign levels keep pointing at the same grid, and a puzzle keeps the use it was made for.
 */
@Entity
@Table(name = "puzzles")
@Getter
public class Puzzle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_code", nullable = false, updatable = false, unique = true, length = 8)
    private String shareCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private PuzzleSource source;

    @Column(nullable = false, updatable = false)
    private int size;

    /** Encoded with {@link Grids#encode}; 0 is an empty cell. */
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String givens;

    /** Encoded with {@link Grids#encode}. */
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String solution;

    @Column(name = "clue_count", nullable = false, updatable = false)
    private int clueCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Difficulty difficulty;

    @Column(nullable = false, updatable = false)
    private int score;

    @Column(nullable = false, updatable = false)
    private double dependency;

    @Column(name = "max_step_cost", nullable = false, updatable = false)
    private double maxStepCost;

    @Column(name = "bottleneck_steps", nullable = false, updatable = false)
    private int bottleneckSteps;

    @Column(name = "hardest_strategy", nullable = false, updatable = false, length = 32)
    private String hardestStrategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "strategy_uses", nullable = false, updatable = false)
    private Map<String, Integer> strategyUses;

    @Column(name = "engine_version", nullable = false, updatable = false, length = 32)
    private String engineVersion;

    @Column(nullable = false, updatable = false)
    private long seed;

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

    public Map<String, Integer> getStrategyUses() {
        return Map.copyOf(strategyUses);
    }
}
