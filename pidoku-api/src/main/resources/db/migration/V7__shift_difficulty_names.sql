-- The difficulty names move up one step: Relaxing becomes Easy, Easy becomes Medium, Medium
-- becomes Hard and Hard becomes Expert. Trivial and Fiendish keep their names. The engine
-- profiles behind them don't change, so every stored puzzle keeps its actual difficulty and
-- just gets the new name.
--
-- daily_puzzles references (puzzle_id, difficulty) in puzzles, so its foreign key is dropped
-- while both tables are relabelled and added back afterwards.
ALTER TABLE daily_puzzles DROP CONSTRAINT daily_puzzles_puzzle_fkey;
ALTER TABLE puzzles DROP CONSTRAINT puzzles_difficulty_check;

UPDATE puzzles SET difficulty = CASE difficulty
    WHEN 'RELAXING' THEN 'EASY'
    WHEN 'EASY'     THEN 'MEDIUM'
    WHEN 'MEDIUM'   THEN 'HARD'
    WHEN 'HARD'     THEN 'EXPERT'
    ELSE difficulty
END;

-- In two steps: (puzzle_date, difficulty) is the primary key, checked row by row, so renaming a
-- date's EASY daily to MEDIUM in one go would collide with its MEDIUM daily not yet renamed.
UPDATE daily_puzzles SET difficulty = 'NEW_' || CASE difficulty
    WHEN 'RELAXING' THEN 'EASY'
    WHEN 'EASY'     THEN 'MEDIUM'
    WHEN 'MEDIUM'   THEN 'HARD'
    WHEN 'HARD'     THEN 'EXPERT'
    ELSE difficulty
END;
UPDATE daily_puzzles SET difficulty = substr(difficulty, 5);

ALTER TABLE puzzles ADD CONSTRAINT puzzles_difficulty_check
    CHECK (difficulty IN ('TRIVIAL', 'EASY', 'MEDIUM', 'HARD', 'EXPERT', 'FIENDISH'));
ALTER TABLE daily_puzzles ADD CONSTRAINT daily_puzzles_puzzle_fkey
    FOREIGN KEY (puzzle_id, difficulty) REFERENCES puzzles (id, difficulty);

-- The dailies stay Easy, Medium and Hard by name, so from now on they're easier puzzles than
-- before (what used to be Relaxing, Easy and Medium). The change takes effect from tomorrow (UTC):
-- dailies from then on are cleared so the generator job fills them with puzzles at the new
-- difficulties, and their puzzles go back to the pool. Tomorrow is already being served at UTC+14
-- (see V5), so a daily of tomorrow's that someone has started is kept rather than swapped out
-- under them. Today's relabelled dailies are kept too. Only the daily difficulties in config are
-- served, so a kept old Hard daily (now Expert) drops out and the job fills the slots left open.
WITH cleared AS (
    DELETE FROM daily_puzzles d
    WHERE d.puzzle_date >= (now() AT TIME ZONE 'UTC')::date + 2
       OR (d.puzzle_date = (now() AT TIME ZONE 'UTC')::date + 1
           AND NOT EXISTS (SELECT 1 FROM attempts a WHERE a.puzzle_id = d.puzzle_id))
    RETURNING d.puzzle_id
)
UPDATE puzzles SET source = 'POOL'
WHERE id IN (SELECT puzzle_id FROM cleared);
