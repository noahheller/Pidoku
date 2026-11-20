/**
 * Player-facing names for the engine's solving techniques (pidoku-engine's StrategyEnum), as the
 * puzzles' `hardestStrategy` sends them.
 */
const NAMES: Record<string, string> = {
  HIDDEN_SINGLE_BOX: 'Hidden single (box)',
  HIDDEN_SINGLE_LINE: 'Hidden single (row or column)',
  NAKED_SINGLE: 'Naked single',
  NAKED_PAIR: 'Naked pair',
  INTERSECTION_REMOVAL: 'Pointing and claiming',
  HIDDEN_PAIR: 'Hidden pair',
  NAKED_TRIPLE: 'Naked triple',
  HIDDEN_TRIPLE: 'Hidden triple',
  NAKED_QUAD: 'Naked quad',
  X_WING: 'X-Wing',
  HIDDEN_QUAD: 'Hidden quad',
  W_WING: 'W-Wing',
};

/**
 * "Hidden pair" for HIDDEN_PAIR. A technique the engine adds later still reads sensibly
 * ("Some new trick" from SOME_NEW_TRICK) until it gets a name here. Null for no technique.
 */
export function strategyName(strategy: string | null | undefined): string | null {
  if (!strategy) return null;
  const known = NAMES[strategy];
  if (known) return known;
  const words = strategy.toLowerCase().split('_').filter(Boolean).join(' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}
