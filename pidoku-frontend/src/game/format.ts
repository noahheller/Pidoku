export function formatTime(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const mm = String(minutes).padStart(hours > 0 ? 2 : 1, '0');
  const ss = String(seconds).padStart(2, '0');
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

export function countClues(givens: readonly number[]): number {
  return givens.filter((v) => v !== 0).length;
}

/** Describes the engine's dependency metric for players, e.g. "About 5 moves open at a time". */
export function describeOpenMoves(dependency: number | null | undefined): string | null {
  if (dependency === null || dependency === undefined) return null;
  const rounded = Math.round(dependency);
  if (rounded <= 1) return 'Usually just one move open at a time';
  return `About ${rounded} moves open at a time`;
}

/**
 * "Tuesday, 17 November" for a YYYY-MM-DD date, in the player's locale. The date is already the
 * player's local date, so it's formatted as written: parsing and printing both in UTC keeps the
 * player's time zone from shifting it by a day.
 */
export function formatDate(date: string): string {
  return new Date(`${date}T00:00:00Z`).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', timeZone: 'UTC' });
}
