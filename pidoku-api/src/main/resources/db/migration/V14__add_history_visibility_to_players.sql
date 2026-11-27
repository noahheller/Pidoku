-- Who can see a player's puzzle history (every puzzle they've solved, random ones included, with
-- their times) on their profile. The same values as profile_visibility (V11), and 'friends' by
-- default too:
--   public:  any player
--   friends: only their friends (and themselves)
--   private: only themselves
-- The history is only shown on the profile, so a stricter profile_visibility hides it as well.
ALTER TABLE players
    ADD COLUMN history_visibility VARCHAR(10) NOT NULL DEFAULT 'friends',
    ADD CONSTRAINT players_history_visibility_check CHECK (history_visibility IN ('public', 'friends', 'private'));
