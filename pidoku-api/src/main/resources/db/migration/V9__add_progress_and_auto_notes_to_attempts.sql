-- After v1: progress sync between devices, and auto notes.
--
-- progress: an unfinished attempt's board as the player last left it, so it follows them to
-- another device: {"values": [...], "notes": [...], "mistakes": n, "activeMs": n} (flat,
-- row-major arrays like the puzzle's grids; notes are bitmasks, bit 1 << d for digit d). Written by
-- PUT /api/attempts/{id}/progress while the attempt is open, kept once it's complete.
-- progress_updated_at: when the server received it, so a device can tell whose copy is newer.
--
-- auto_notes_used: the player pressed Auto notes (Expert and Fiendish only) during the attempt.
-- It changes solve times, so calibration (milestone 9) has to tell those solves apart.
ALTER TABLE attempts
    ADD COLUMN progress            JSONB,
    ADD COLUMN progress_updated_at TIMESTAMPTZ,
    ADD COLUMN auto_notes_used     BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT attempts_progress_check CHECK ((progress IS NULL) = (progress_updated_at IS NULL));

-- The player's latest attempts, for their Recent list.
CREATE INDEX attempts_player_started_idx ON attempts (player_id, started_at DESC);
