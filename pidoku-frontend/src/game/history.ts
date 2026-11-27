import type { HistoryEntry, OtherSolve, ProfileVisibility } from '../api/client';
import type { TimeToBeat } from './leaderboardStats';
import { rankedMs } from './penalties';

/** The signed-in player's own first solve of a history's puzzle, if they've solved it. */
export function yourSolve(entry: HistoryEntry): OtherSolve | undefined {
  return entry.others.find((o) => o.you);
}

/** Whose history it is, as the profile says. */
export interface HistoryOwner {
  displayName: string | null;
  /** It's the signed-in player's own. */
  you: boolean;
  /** They're the signed-in player's friend. */
  friend: boolean;
}

/**
 * The time to beat when the player opens a puzzle from someone's history: that person's first
 * solve. Null on the player's own history, and for a puzzle the player has already solved (only a
 * first solve ranks, so there's nothing left to race for).
 */
export function challengeFrom(entry: HistoryEntry, owner: HistoryOwner): TimeToBeat | null {
  if (owner.you || yourSolve(entry)) return null;
  return { name: owner.displayName, elapsedMs: entry.solve.elapsedMs, penaltyMs: entry.solve.penaltyMs, friend: owner.friend };
}

/** How the player's solve compares with the history owner's, by ranked time. */
export interface Versus {
  outcome: 'faster' | 'slower' | 'tied';
  /** How far apart the two ranked times are. */
  byMs: number;
}

/** The player's solve against the owner's, or null if the player hasn't solved it. */
export function versus(entry: HistoryEntry): Versus | null {
  const yours = yourSolve(entry);
  if (!yours) return null;
  const diff = rankedMs(yours) - rankedMs(entry.solve);
  return { outcome: diff < 0 ? 'faster' : diff > 0 ? 'slower' : 'tied', byMs: Math.abs(diff) };
}

const OPENNESS: readonly ProfileVisibility[] = ['private', 'friends', 'public'];

/**
 * Who actually sees the history: it's only shown on the profile, so it's never seen more widely
 * than the profile is.
 */
export function historyShownTo(profile: ProfileVisibility, history: ProfileVisibility): ProfileVisibility {
  return OPENNESS[Math.min(OPENNESS.indexOf(profile), OPENNESS.indexOf(history))];
}
