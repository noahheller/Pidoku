-- Milestone 5: friends, so a daily's leaderboard can be filtered to them.
--
-- friend_code: what a player gives friends to add them, 8 characters from the share-code alphabet
-- (see ShareCodeGenerator). Null until the player first asks for it (GET /api/players/me), so
-- existing players need no backfill.
ALTER TABLE players
    ADD COLUMN friend_code VARCHAR(8),
    ADD CONSTRAINT players_friend_code_key UNIQUE (friend_code);

-- Friendship is mutual: adding someone by their code makes both players friends, with no request
-- to accept. Each friendship is stored as two rows, one per direction, so "my friends" is a
-- lookup on the primary key. FriendService always inserts and deletes both together.
CREATE TABLE friendships (
    player_id  BIGINT      NOT NULL,
    friend_id  BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT friendships_pkey PRIMARY KEY (player_id, friend_id),
    CONSTRAINT friendships_player_fkey FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT friendships_friend_fkey FOREIGN KEY (friend_id) REFERENCES players (id),
    CONSTRAINT friendships_not_self_check CHECK (player_id <> friend_id)
);
