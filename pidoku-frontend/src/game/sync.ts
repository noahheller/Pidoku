import type { Attempt } from '../api/client';
import type { SavedProgress } from './game';

/**
 * Progress sync between devices: an unfinished attempt's board is saved to the server (see
 * Game's useProgressSync), and opening the puzzle elsewhere takes whichever copy is newer, this
 * browser's or the server's. Last write wins; playing on two devices at once isn't worth more.
 */

/** How often a board being played is saved to the server, at most. */
export const SYNC_INTERVAL_MS = 10_000;

/**
 * The server's copy of the board, if it's newer than this browser's (or this browser has none).
 * Null to keep this browser's: it's newer, already finished, or the server has nothing.
 */
export function newerOnServer(local: SavedProgress | null, attempt: Attempt): SavedProgress | null {
  const { progress, progressUpdatedAt } = attempt;
  if (!progress || !progressUpdatedAt || attempt.completedAt !== null) return null;
  if (local?.completed) return null;
  const serverAt = Date.parse(progressUpdatedAt);
  if (!Number.isFinite(serverAt)) return null;
  // A local save from before saves were timestamped can't be compared: the server's is newer
  // unless this browser has played further.
  if (local && (local.savedAt === undefined ? local.elapsedMs >= progress.activeMs : local.savedAt >= serverAt)) return null;
  return {
    values: progress.values,
    notes: progress.notes,
    mistakes: progress.mistakes,
    elapsedMs: progress.activeMs,
    completed: false,
    autoNotesUsed: attempt.autoNotesUsed ?? false,
    savedAt: serverAt,
  };
}

/** Whether two boards differ in what's worth sending (the timer alone isn't). */
export function boardChanged(a: SavedProgress | null, b: SavedProgress): boolean {
  if (!a) return true;
  return a.mistakes !== b.mistakes || a.autoNotesUsed !== b.autoNotesUsed || a.values.some((v, i) => v !== b.values[i]) || a.notes.some((n, i) => n !== b.notes[i]);
}
