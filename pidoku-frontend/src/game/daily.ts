import { formatTime } from './format';

/**
 * The first day of daily puzzles: "Pidoku #1". Fixed for good once shared, since every share
 * line's number counts from it.
 */
export const LAUNCH_DATE = '2026-11-17';

const DAY_MS = 24 * 60 * 60 * 1000;

/** "Pidoku #123": days since launch, launch day being #1. Dates are YYYY-MM-DD. */
export function dailyNumber(date: string): number {
  // Both parsed as UTC midnights, so daylight saving can't make a day 23 or 25 hours long.
  return Math.round((Date.parse(`${date}T00:00:00Z`) - Date.parse(`${LAUNCH_DATE}T00:00:00Z`)) / DAY_MS) + 1;
}

/**
 * "Pidoku #123 · " for a date from launch day on, or "" before it: dailies were generated before
 * launch, and "#0" or "#-3" would look broken.
 */
export function dailyLabel(date: string): string {
  const n = dailyNumber(date);
  return n >= 1 ? `Pidoku #${n} · ` : '';
}

export interface ShareDetails {
  /** e.g. "Hard" */
  difficulty: string | null;
  /** YYYY-MM-DD for a daily; null for other puzzles, which get no number. */
  dailyDate: string | null;
  elapsedMs: number;
  /** Null for a puzzle without a solution, where mistakes aren't counted. */
  mistakes: number | null;
  /** A link to the puzzle, or null if it has none (a puzzle loaded from a file). */
  url: string | null;
}

/**
 * What the player pastes to friends, e.g. "Pidoku #123 Hard 4:12 ✓" and a link on the next line.
 * ✓ is a solve without mistakes; otherwise it says how many, e.g. "2✗".
 */
export function shareLine({ difficulty, dailyDate, elapsedMs, mistakes, url }: ShareDetails): string {
  const parts = ['Pidoku'];
  if (dailyDate) parts.push(`#${dailyNumber(dailyDate)}`);
  if (difficulty) parts.push(difficulty);
  parts.push(formatTime(elapsedMs));
  if (mistakes !== null) parts.push(mistakes === 0 ? '✓' : `${mistakes}✗`);
  const line = parts.join(' ');
  return url ? `${line}\n${url}` : line;
}

/** The share link for a puzzle from the API, on this site. */
export function shareUrl(shareCode: string, origin: string = window.location.origin): string {
  return `${origin}/p/${shareCode}`;
}

/** A friend's invite link (`/f/{friendCode}`), on this site. */
export function friendUrl(friendCode: string, origin: string = window.location.origin): string {
  return `${origin}/f/${friendCode}`;
}
