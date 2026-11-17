-- A player is one Firebase account. Every visitor gets an anonymous account without signing up,
-- so every player has a uid; the row is created the first time the API needs it.
CREATE TABLE players (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- The Firebase uid (the ID token's "sub"). Firebase allows up to 128 characters.
    firebase_uid VARCHAR(128) NOT NULL,
    display_name VARCHAR(40),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT players_firebase_uid_key UNIQUE (firebase_uid)
);

-- One go at a puzzle by a player. The row is created when the player starts the puzzle, so
-- started_at and completed_at are both the server's time and elapsed_ms (their difference) can't
-- be claimed by the client. mistakes is what the client reports.
CREATE TABLE attempts (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_id    BIGINT      NOT NULL,
    puzzle_id    BIGINT      NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    elapsed_ms   BIGINT,
    mistakes     INTEGER     NOT NULL DEFAULT 0,
    hints_used   INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT attempts_player_fkey FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT attempts_puzzle_fkey FOREIGN KEY (puzzle_id) REFERENCES puzzles (id),
    -- Unfinished attempts have neither, finished ones both.
    CONSTRAINT attempts_completion_check CHECK ((completed_at IS NULL) = (elapsed_ms IS NULL)),
    CONSTRAINT attempts_elapsed_ms_check CHECK (elapsed_ms >= 0),
    CONSTRAINT attempts_mistakes_check CHECK (mistakes >= 0),
    CONSTRAINT attempts_hints_used_check CHECK (hints_used >= 0)
);

-- A player has at most one unfinished attempt at a puzzle: starting it again continues that one.
-- Finished attempts aren't limited, so a puzzle can be replayed.
CREATE UNIQUE INDEX attempts_open_key ON attempts (player_id, puzzle_id) WHERE completed_at IS NULL;

-- Which puzzles a player has seen (the random endpoint skips them).
CREATE INDEX attempts_player_puzzle_idx ON attempts (player_id, puzzle_id);

-- Fastest finished attempts at a puzzle, for leaderboards (milestone 6).
CREATE INDEX attempts_puzzle_elapsed_idx ON attempts (puzzle_id, elapsed_ms) WHERE completed_at IS NOT NULL;
