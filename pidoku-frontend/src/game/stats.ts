import type { DifficultyStats } from '../api/client';
import { formatTime } from './format';

/** Clean solves as a whole percentage of those counted, or null when none are counted. */
export function cleanPercent(stats: Pick<DifficultyStats, 'solved' | 'cleanSolves'>): number | null {
  return stats.solved > 0 ? Math.round((stats.cleanSolves / stats.solved) * 100) : null;
}

/** Changes smaller than this read as "about the same". */
export const TREND_SAME_MS = 5000;

/**
 * How the last 30 days compare with all time, e.g. "Last 30 days: 1:05 faster than your average".
 * Null when there's nothing to compare: no recent solves, or every counted solve is recent (the two
 * averages are then the same solves).
 */
export function trendLine(stats: Pick<DifficultyStats, 'solved' | 'averageMs' | 'recentSolved' | 'recentAverageMs'>): string | null {
  if (stats.averageMs === null || stats.recentAverageMs === null || stats.recentSolved === 0 || stats.recentSolved === stats.solved) return null;
  const diff = stats.recentAverageMs - stats.averageMs;
  const solves = `${stats.recentSolved} ${stats.recentSolved === 1 ? 'solve' : 'solves'}`;
  if (Math.abs(diff) < TREND_SAME_MS) return `Last 30 days (${solves}): about the same as your average`;
  return `Last 30 days (${solves}): ${formatTime(Math.abs(diff))} ${diff < 0 ? 'faster' : 'slower'} than your average`;
}

/** Average mistakes per solve, to one decimal place ("0.7"). */
export function formatMistakes(average: number): string {
  return (Math.round(average * 10) / 10).toFixed(1);
}
