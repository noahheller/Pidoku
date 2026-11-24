-- Who can see a player's profile (streaks, totals, best times, recent dailies):
--   public:  any player, e.g. from a leaderboard
--   friends: only their friends (and themselves). What profiles were before this column existed.
--   private: only themselves
ALTER TABLE players
    ADD COLUMN profile_visibility VARCHAR(10) NOT NULL DEFAULT 'friends',
    ADD CONSTRAINT players_profile_visibility_check CHECK (profile_visibility IN ('public', 'friends', 'private'));
