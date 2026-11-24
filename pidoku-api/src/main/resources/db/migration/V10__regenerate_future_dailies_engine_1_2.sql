-- Engine 1.2.0 stops the Easy, Medium and Hard profiles overlapping: score windows 25-40, 45-69
-- and 70-120, and Medium capped at 36 clues (Easy needs 38+). Dailies already stored were made
-- with the old profiles, so the ones no one can have been served yet are cleared and the next
-- generator run fills them with the new ones.
--
-- As in V5: the API serves a date from when it's today at UTC+14, i.e. from the day before in UTC,
-- so dates from two days after today in UTC haven't been served to anyone. Today's and tomorrow's
-- dailies are kept as they are; clearing tomorrow's would leave it empty at UTC+14 until the
-- nightly job (03:00 New York, 08:00 UTC) ran. That run always comes before the first cleared
-- date opens (10:00 UTC the next day), so no date goes without dailies.
--
-- Their puzzles go back to the random pool, as in V5 and V7: none was ever served as a daily, and
-- each is still a valid puzzle of the difficulty it was labelled with when generated.
WITH cleared AS (
    DELETE FROM daily_puzzles
    WHERE puzzle_date >= (now() AT TIME ZONE 'UTC')::date + 2
    RETURNING puzzle_id
)
UPDATE puzzles SET source = 'POOL'
WHERE id IN (SELECT puzzle_id FROM cleared);
