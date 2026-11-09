export function formatTime(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const mm = String(minutes).padStart(hours > 0 ? 2 : 1, '0');
  const ss = String(seconds).padStart(2, '0');
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

/** "INTERSECTION_REMOVAL" -> "Intersection removal" */
export function humanizeStrategy(strategy: string): string {
  const words = strategy.toLowerCase().split('_');
  return [words[0].charAt(0).toUpperCase() + words[0].slice(1), ...words.slice(1)].join(' ').replace('x wing', 'X-Wing').replace('w wing', 'W-Wing');
}

export function countClues(givens: readonly number[]): number {
  return givens.filter((v) => v !== 0).length;
}
