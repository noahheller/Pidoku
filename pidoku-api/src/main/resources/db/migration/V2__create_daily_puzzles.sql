-- The daily puzzles: for each UTC date, at most one puzzle per difficulty. Which difficulties
-- get a daily is application config (pidoku.daily.difficulties), so it isn't fixed here.
-- A puzzle is a daily at most once.
CREATE TABLE daily_puzzles (
    puzzle_date DATE        NOT NULL,
    difficulty  VARCHAR(16) NOT NULL,
    puzzle_id   BIGINT      NOT NULL,

    CONSTRAINT daily_puzzles_pkey PRIMARY KEY (puzzle_date, difficulty),
    CONSTRAINT daily_puzzles_puzzle_id_key UNIQUE (puzzle_id),
    -- Referencing the puzzle's (id, difficulty) pair, not just its id, makes the database
    -- guarantee that a medium daily really is a medium puzzle.
    CONSTRAINT daily_puzzles_puzzle_fkey FOREIGN KEY (puzzle_id, difficulty) REFERENCES puzzles (id, difficulty)
);
