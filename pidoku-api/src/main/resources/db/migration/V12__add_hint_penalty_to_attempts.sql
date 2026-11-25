-- Milestone 6: hints, and time penalties on the leaderboards.
--
-- hint_penalty_ms: the time added for hints, charged by POST /api/attempts/{id}/hint as each step
-- is revealed (10 s for "where", 20 s for "what to look for", 60 s for the answer; see
-- attempt/Penalties.java). hints_used (V3) counts the steps revealed.
--
-- last_hint: the hint being shown, so tapping Hint again reveals the next step of the same move
-- rather than charging for a new one: {"key": "...", "step": 1-3, "eliminations": [[cell, digit], ...]}.
-- eliminations are the candidates cleared by elimination hints already shown in full, which the
-- player's digits alone can't show.
--
-- penalty_ms: what the leaderboards add to the game's timer: 5 s x mistakes squared (1 mistake 5 s,
-- 2 make 20 s, 3 make 45 s), so a slip is cheap but guessing isn't, plus the hint penalty.
-- Generated so the formula lives in one place; the mistake part applies to past solves too.
ALTER TABLE attempts
    ADD COLUMN hint_penalty_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_hint       JSONB,
    ADD CONSTRAINT attempts_hint_penalty_ms_check CHECK (hint_penalty_ms >= 0);

ALTER TABLE attempts
    ADD COLUMN penalty_ms BIGINT GENERATED ALWAYS AS (mistakes::BIGINT * mistakes * 5000 + hint_penalty_ms) STORED;
