import { describe, expect, it } from 'vitest';
import { describeOpenMoves, humanizeStrategy } from './format';

describe('humanizeStrategy', () => {
  it('names the split hidden singles', () => {
    expect(humanizeStrategy('HIDDEN_SINGLE_BOX')).toBe('Hidden single (box)');
    expect(humanizeStrategy('HIDDEN_SINGLE_LINE')).toBe('Hidden single (row/column)');
  });

  it('formats other strategies from their enum names', () => {
    expect(humanizeStrategy('NAKED_SINGLE')).toBe('Naked single');
    expect(humanizeStrategy('INTERSECTION_REMOVAL')).toBe('Intersection removal');
    expect(humanizeStrategy('X_WING')).toBe('X-Wing');
    expect(humanizeStrategy('W_WING')).toBe('W-Wing');
    expect(humanizeStrategy('HIDDEN_SINGLE')).toBe('Hidden single'); // older engine output
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
