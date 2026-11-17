-- Milestone 4: what the client knows about a solve, sent with the completing call.
--
-- active_ms: the client's own timer, which pauses while the tab is hidden. elapsed_ms (the
-- server's wall-clock time) is what leaderboards rank; active_ms is for calibrating difficulty
-- (milestone 9), where time spent away is noise. Null while unfinished, and for solves recorded
-- before this column existed or sent by a client that didn't report it.
--
-- late: the completion reached the server some time after the puzzle was solved (offline, or the
-- API was down), and the client said how long ago (lateByMs). completed_at and elapsed_ms are
-- then moved back by that delay. Leaderboards show late solves with a marker; they count for
-- streaks like any other.
ALTER TABLE attempts
    ADD COLUMN active_ms BIGINT,
    ADD COLUMN late      BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT attempts_active_ms_check CHECK (active_ms >= 0),
    ADD CONSTRAINT attempts_active_ms_completion_check CHECK (completed_at IS NOT NULL OR active_ms IS NULL);

