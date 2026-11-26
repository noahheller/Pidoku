import type { Leaderboard, LeaderboardEntry } from '../api/client';
import { formatTime } from './format';

/**
 * Fun facts about a leaderboard, worked out in the browser from what the API already sent, so
 * they cost no extra database work.
 */

/** The time an entry is ranked by: the game's time plus its penalties. */
export function rankedMs(entry: Pick<LeaderboardEntry, 'elapsedMs' | 'penaltyMs'>): number {
  return entry.elapsedMs + (entry.penaltyMs ?? 0);
}

/** Two solves this close are a photo finish. */
export const PHOTO_FINISH_MS = 1000;

/** "1st", "2nd", "3rd", "11th", "22nd"… */
export function ordinal(n: number): string {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) return `${n}th`;
  return `${n}${['th', 'st', 'nd', 'rd'][n % 10] ?? 'th'}`;
}

/**
 * The share of the other ranked players who were slower than the player, rounded down so it never
 * overstates. Players tied with them don't count as slower. Null when they're the only one ranked.
 */
export function fasterThanPercent(rank: number, total: number, tiedWithYou = 1): number | null {
  if (total <= 1) return null;
  const slower = total - rank - (tiedWithYou - 1);
  return Math.floor((Math.max(0, slower) / (total - 1)) * 100);
}

/** The time between the player and their neighbours on the list, when the neighbours are on it. */
export interface Gaps {
  /** The next-faster player and how far ahead of the player they were. */
  behind: { name: string | null; ms: number } | null;
  /** The next-slower player and how far behind the player they were. */
  ahead: { name: string | null; ms: number } | null;
}

export function gaps(entries: LeaderboardEntry[]): Gaps | null {
  const i = entries.findIndex((e) => e.you);
  if (i < 0) return null;
  const you = entries[i];
  const faster = entries[i - 1];
  const slower = entries[i + 1];
  return {
    behind: faster ? { name: faster.displayName, ms: rankedMs(you) - rankedMs(faster) } : null,
    ahead: slower ? { name: slower.displayName, ms: rankedMs(slower) - rankedMs(you) } : null,
  };
}

/** Positions in `entries` that finished within a second of the entry just above or below. */
export function photoFinishes(entries: LeaderboardEntry[]): Set<number> {
  const close = new Set<number>();
  for (let i = 1; i < entries.length; i++) {
    if (rankedMs(entries[i]) - rankedMs(entries[i - 1]) < PHOTO_FINISH_MS) {
      close.add(i - 1);
      close.add(i);
    }
  }
  return close;
}

/** A sentence about where the player stands, e.g. "Faster than 80% of players" or "2nd of 4 friends". */
export function standing(board: Leaderboard): string | null {
  const you = board.you;
  if (!you) return null;
  const clean = board.cleanOnly ? ' clean' : '';
  if (board.friendsOnly) {
    if (board.total <= 1) return null;
    return `You're ${ordinal(you.rank)} of ${board.total} among you and your friends${clean ? ' on clean solves' : ''}.`;
  }
  const tied = board.entries.filter((e) => e.rank === you.rank).length || 1;
  const percent = fasterThanPercent(you.rank, board.total, tied);
  if (percent === null) return null;
  if (you.rank === 1 && tied === 1) return `Fastest${clean} solve of all ${board.total} players!`;
  return `Faster than ${percent}% of${clean} solvers.`;
}

/** "12s behind Ada · 3s ahead of Bob", or null if the player isn't on the list. */
export function gapLine(board: Leaderboard): string | null {
  const g = gaps(board.entries);
  if (!g || (!g.behind && !g.ahead)) return null;
  const name = (n: string | null) => n ?? 'Anonymous';
  const parts: string[] = [];
  if (g.behind) parts.push(g.behind.ms === 0 ? `tied with ${name(g.behind.name)}` : `${formatGap(g.behind.ms)} behind ${name(g.behind.name)}`);
  if (g.ahead) parts.push(g.ahead.ms === 0 ? `tied with ${name(g.ahead.name)}` : `${formatGap(g.ahead.ms)} ahead of ${name(g.ahead.name)}`);
  const line = parts.join(' · ');
  return line.charAt(0).toUpperCase() + line.slice(1);
}

/** "<0.1s", "0.4s", "12s", "1:05". */
export function formatGap(ms: number): string {
  if (ms < 100) return '<0.1s';
  if (ms < 1000) return `${(ms / 1000).toFixed(1)}s`;
  if (ms < 60_000) return `${Math.floor(ms / 1000)}s`;
  return formatTime(ms);
}

/** The solve a challenge link puts up to beat: who, and their ranked time. */
export interface TimeToBeat {
  /** Null if the player hasn't picked a display name. */
  name: string | null;
  /** The game's time. */
  elapsedMs: number;
  /** Added for mistakes and hints; `elapsedMs` plus this is what ranks. */
  penaltyMs: number;
  /** Whether the solve is a friend's (from the friends board). */
  friend: boolean;
}

/**
 * The time to beat on a shared puzzle: the fastest friend's solve if any friend has solved it,
 * otherwise the fastest overall. The player's own solve never counts (they can't challenge
 * themselves). Null when no one else has solved it.
 *
 * @param friends the friends-only board, or null when signed out (or it couldn't be fetched)
 */
export function timeToBeat(everyone: Leaderboard | null, friends: Leaderboard | null): TimeToBeat | null {
  const pick = (board: Leaderboard | null, friend: boolean): TimeToBeat | null => {
    const top = board?.entries.find((e) => !e.you);
    return top ? { name: top.displayName, elapsedMs: top.elapsedMs, penaltyMs: top.penaltyMs ?? 0, friend } : null;
  };
  return pick(friends, true) ?? pick(everyone, false);
}
