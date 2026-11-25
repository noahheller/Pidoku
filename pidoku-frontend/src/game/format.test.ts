import { describe, expect, it } from 'vitest';
import { describeOpenMoves, formatPenalty } from './format';

describe('formatPenalty', () => {
  it('is empty for none, seconds under a minute, and minutes past that', () => {
    expect(formatPenalty(0)).toBe('');
    expect(formatPenalty(40_000)).toBe('+40s');
    expect(formatPenalty(90_000)).toBe('+1:30');
  });
});

describe('describeOpenMoves', () => {
  it('rounds the average for players', () => {
    expect(describeOpenMoves(5.3)).toBe('About 5 moves open at a time');
    expect(describeOpenMoves(12.8)).toBe('About 13 moves open at a time');
  });

  it('handles very narrow puzzles and missing data', () => {
    expect(describeOpenMoves(0.6)).toBe('Usually just one move open at a time');
    expect(describeOpenMoves(null)).toBeNull();
    expect(describeOpenMoves(undefined)).toBeNull();
  });
});
