export function formatTime(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const mm = String(minutes).padStart(hours > 0 ? 2 : 1, '0');
  const ss = String(seconds).padStart(2, '0');
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

/**
 * Time added to a solve for mistakes and hints, as shown after it: "+40s", "+1:30". Empty for none.
 */
export function formatPenalty(ms: number): string {
  if (ms <= 0) return '';
  return ms < 60_000 ? `+${Math.floor(ms / 1000)}s` : `+${formatTime(ms)}`;
}

export function countClues(givens: readonly number[]): number {
  return givens.filter((v) => v !== 0).length;
}

/** The text with its first letter in upper case: "hard" → "Hard". */
export function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/**
 * "Tuesday, 17 November" for a YYYY-MM-DD date, in the player's locale. The date is already the
 * player's local date, so it's formatted as written: parsing and printing both in UTC keeps the
 * player's time zone from shifting it by a day.
 */
export function formatDate(date: string): string {
  return new Date(`${date}T00:00:00Z`).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', timeZone: 'UTC' });
}
