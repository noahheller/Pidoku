import { describe, expect, it } from 'vitest';
import { cleanPercent, formatMistakes, trendLine } from './stats';

describe('cleanPercent', () => {
  it('is the share of counted solves with no mistakes and no hints', () => {
    expect(cleanPercent({ solved: 3, cleanSolves: 2 })).toBe(67);
    expect(cleanPercent({ solved: 0, cleanSolves: 0 })).toBeNull();
  });
});

describe('trendLine', () => {
  const stats = { solved: 10, averageMs: 300_000, recentSolved: 4, recentAverageMs: 235_000 };

  it('says how much faster or slower the last 30 days were', () => {
    expect(trendLine(stats)).toBe('Last 30 days (4 solves): 1:05 faster than your average');
    expect(trendLine({ ...stats, recentSolved: 1, recentAverageMs: 360_000 })).toBe('Last 30 days (1 solve): 1:00 slower than your average');
  });

  it('calls a small change about the same', () => {
    expect(trendLine({ ...stats, recentAverageMs: 297_000 })).toBe('Last 30 days (4 solves): about the same as your average');
  });

  it('has nothing to say without recent solves, or when every solve is recent', () => {
    expect(trendLine({ ...stats, recentSolved: 0, recentAverageMs: null })).toBeNull();
    expect(trendLine({ ...stats, recentSolved: 10 })).toBeNull();
    expect(trendLine({ solved: 0, averageMs: null, recentSolved: 0, recentAverageMs: null })).toBeNull();
  });
});

describe('formatMistakes', () => {
  it('rounds to one decimal place', () => {
    expect(formatMistakes(0.6666)).toBe('0.7');
    expect(formatMistakes(0)).toBe('0.0');
    expect(formatMistakes(2)).toBe('2.0');
  });
});
