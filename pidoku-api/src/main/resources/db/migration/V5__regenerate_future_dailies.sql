-- Until now the generator job filled daily slots (up to 31 days ahead) with puzzles from the random
-- pool, so a daily could be a puzzle someone had already played or solved through the Random
-- buttons. Dailies are now always generated fresh. This clears the slots already filled that no
-- one can have been served as a daily yet, so the next generator run fills them with new puzzles.
--
-- The API serves a date from when it's today at UTC+14, i.e. from the day before in UTC. So dates
-- from two days after today in UTC haven't been served to anyone; today's and tomorrow's dailies
-- (which may have been) are kept as they are.
--
-- Their puzzles go back to the pool, where most of them came from: none was ever served as a
-- daily, and the random endpoint skips puzzles a player has already started.
WITH cleared AS (
    DELETE FROM daily_puzzles
    WHERE puzzle_date >= (now() AT TIME ZONE 'UTC')::date + 2
    RETURNING puzzle_id
)
UPDATE puzzles SET source = 'POOL'
WHERE id IN (SELECT puzzle_id FROM cleared);
