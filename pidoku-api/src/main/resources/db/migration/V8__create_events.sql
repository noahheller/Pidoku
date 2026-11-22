-- After v1: analytics events, for what attempts don't record (see "Monetisation and analytics" in
-- docs/ROADMAP.md). One row per thing worth counting: a share tap, a visit from a share link, a
-- puzzle left unfinished, the tutorial finished or skipped.
--
-- Rows carry the player's id only, never an email or display name, and go when the player deletes
-- their account. type is checked by the API (EventType), not here, so a new type needs no migration.
-- detail is a small JSON object whose fields depend on the type.
CREATE TABLE events (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_id  BIGINT      NOT NULL,
    type       VARCHAR(32) NOT NULL,
    puzzle_id  BIGINT,
    detail     JSONB       NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT events_player_fkey FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT events_puzzle_fkey FOREIGN KEY (puzzle_id) REFERENCES puzzles (id),
    CONSTRAINT events_detail_object_check CHECK (jsonb_typeof(detail) = 'object')
);

-- Counting one kind of event over time ("share taps per week").
CREATE INDEX events_type_created_idx ON events (type, created_at);
-- Deleting a player's events.
CREATE INDEX events_player_idx ON events (player_id);
