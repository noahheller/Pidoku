-- Every puzzle the game can serve: the random pool, daily puzzles, campaign levels and
-- (later) players' custom puzzles. Grids are stored row-major, one character per cell,
-- with 0 for an empty cell. Every Pidoku digit is a single decimal digit, so this works
-- for any board size.
CREATE TABLE puzzles (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    share_code       VARCHAR(8)       NOT NULL,
    source           VARCHAR(16)      NOT NULL,
    size             INTEGER          NOT NULL,
    givens           TEXT             NOT NULL,
    solution         TEXT             NOT NULL,
    clue_count       INTEGER          NOT NULL,
    difficulty       VARCHAR(16)      NOT NULL,
    score            INTEGER          NOT NULL,
    dependency       DOUBLE PRECISION NOT NULL,
    max_step_cost    DOUBLE PRECISION NOT NULL,
    bottleneck_steps INTEGER          NOT NULL,
    hardest_strategy VARCHAR(32)      NOT NULL,
    -- Strategy name -> times the human solver used it, e.g. {"NAKED_PAIR": 3}.
    strategy_uses    JSONB            NOT NULL,
    -- PidokuEngine.VERSION when the puzzle was generated; with seed it identifies how it was made.
    engine_version   VARCHAR(32)      NOT NULL,
    seed             BIGINT           NOT NULL,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),

    CONSTRAINT puzzles_share_code_key UNIQUE (share_code),
    -- Lets other tables reference a puzzle together with its difficulty (see daily_puzzles).
    CONSTRAINT puzzles_id_difficulty_key UNIQUE (id, difficulty),
    CONSTRAINT puzzles_source_check
        CHECK (source IN ('POOL', 'DAILY', 'CAMPAIGN', 'CUSTOM')),
    CONSTRAINT puzzles_difficulty_check
        CHECK (difficulty IN ('TRIVIAL', 'RELAXING', 'EASY', 'MEDIUM', 'HARD', 'FIENDISH')),
    CONSTRAINT puzzles_size_check CHECK (size > 0),
    CONSTRAINT puzzles_givens_check
        CHECK (char_length(givens) = size * size AND givens ~ '^[0-9]+$'),
    CONSTRAINT puzzles_solution_check
        CHECK (char_length(solution) = size * size AND solution ~ '^[1-9]+$'),
    CONSTRAINT puzzles_clue_count_check CHECK (clue_count BETWEEN 0 AND size * size),
    CONSTRAINT puzzles_strategy_uses_check CHECK (jsonb_typeof(strategy_uses) = 'object')
);

-- Drawing a random puzzle of one difficulty from the pool.
CREATE INDEX puzzles_source_difficulty_idx ON puzzles (source, difficulty);
