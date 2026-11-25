import type { RecentPuzzle } from '../api/client';
import type { PuzzleDef } from './puzzleFile';
import { loadProgress, loadResult } from './storage';

/**
 * The Recent list on the home screen: the last puzzles played, unfinished first ("Continue"),
 * then the latest solves with their times. Signed in it comes from the server's attempts, so it's
 * the same on every device; signed out (or offline), from the puzzles played in this browser.
 */

/** How many puzzles the list holds; the API returns the same number. */
export const MAX_RECENT = 10;

export interface RecentEntry {
  puzzle: PuzzleDef;
  completed: boolean;
  /** The solve's time, or the timer so far; null if unknown. */
  timeMs: number | null;
  /** Auto notes was used (only offered on Expert and Fiendish). */
  autoNotesUsed: boolean;
  /** When it was last played (ms since 1970); 0 if unknown. */
  lastPlayedAt: number;
}

/**
 * Adds a puzzle being opened to this browser's list (moving it to the end if it's there), and
 * says which puzzles fell off the end: their saved progress is the caller's to clear.
 */
export function addRecent(list: readonly PuzzleDef[], puzzle: PuzzleDef, max: number = MAX_RECENT): { list: PuzzleDef[]; dropped: PuzzleDef[] } {
  const next = [...list.filter((p) => p.id !== puzzle.id), puzzle];
  const cut = Math.max(0, next.length - max);
  return { list: next.slice(cut), dropped: next.slice(0, cut) };
}

/** This browser's list as entries, from what it has saved. Puzzles opened but never started are left out. */
export function localEntries(list: readonly PuzzleDef[]): RecentEntry[] {
  return list.flatMap((puzzle) => {
    const progress = loadProgress(puzzle.id);
    if (!progress) return [];
    const result = progress.completed ? loadResult(puzzle.id) : null;
    return [{ puzzle, completed: progress.completed, timeMs: result?.elapsedMs ?? progress.elapsedMs, autoNotesUsed: progress.autoNotesUsed ?? false, lastPlayedAt: progress.savedAt ?? 0 }];
  });
}

/** The server's list as entries. */
export function serverEntries(recent: readonly RecentPuzzle[]): RecentEntry[] {
  return recent.map((r) => ({ puzzle: r.puzzle, completed: r.completed, timeMs: r.timeMs, autoNotesUsed: r.autoNotesUsed ?? false, lastPlayedAt: Date.parse(r.lastPlayedAt) || 0 }));
}

/**
 * The list to show: the server's entries, plus this browser's puzzles the server doesn't know
 * (offline puzzles, or ones played before signing in), the {@link MAX_RECENT} most recent of them,
 * unfinished first.
 */
export function recentList(server: readonly RecentEntry[] | null, local: readonly RecentEntry[], max: number = MAX_RECENT): RecentEntry[] {
  const onServer = new Set((server ?? []).map((e) => e.puzzle.id));
  // Newest first; the sort is stable, so entries without a time keep their order (this browser's
  // list is oldest first, hence reversed).
  const all = [...(server ?? []), ...[...local].reverse().filter((e) => !onServer.has(e.puzzle.id))];
  const latest = all.sort((a, b) => b.lastPlayedAt - a.lastPlayedAt).slice(0, max);
  return [...latest.filter((e) => !e.completed), ...latest.filter((e) => e.completed)];
}
