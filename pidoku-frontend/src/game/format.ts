export function formatTime(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const mm = String(minutes).padStart(hours > 0 ? 2 : 1, '0');
  const ss = String(seconds).padStart(2, '0');
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`;
}

const STRATEGY_NAMES: Record<string, string> = {
  HIDDEN_SINGLE_BOX: 'Hidden single (box)',
  HIDDEN_SINGLE_LINE: 'Hidden single (row/column)',
  X_WING: 'X-Wing',
  W_WING: 'W-Wing',
};

/** "INTERSECTION_REMOVAL" -> "Intersection removal" */
export function humanizeStrategy(strategy: string): string {
  if (STRATEGY_NAMES[strategy]) return STRATEGY_NAMES[strategy];
  const words = strategy.toLowerCase().split('_');
  return [words[0].charAt(0).toUpperCase() + words[0].slice(1), ...words.slice(1)].join(' ');
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
