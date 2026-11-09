import { describe, expect, it } from 'vitest';
import { bundledPuzzles } from '../puzzles';
import { findConflicts, isPeer, isPlayable, isSolved, remainingCounts, rulesFor } from './rules';

const rules = rulesFor(9);

describe('rulesFor(9)', () => {
  it('uses the first nine digits of pi', () => {
    expect(rules.digits).toEqual([1, 2, 3, 4, 5, 6, 9]);
    expect(rules.frequency[1]).toBe(2);
    expect(rules.frequency[5]).toBe(2);
    expect(rules.frequency[3]).toBe(1);
    expect(rules.frequency[7]).toBe(0);
  });

  it('rejects unsupported sizes', () => {
    expect(() => rulesFor(8)).toThrow();
    expect(() => rulesFor(36)).toThrow();
  });

  it('treats out-of-range values as unplayable', () => {
    for (const v of [-1, 0, 7, 8, 10, 1.5]) expect(isPlayable(rules, v)).toBe(false);
    expect(isPlayable(rules, 9)).toBe(true);
  });

  it('knows peers', () => {
    expect(isPeer(rules, 0, 8)).toBe(true); // same row
    expect(isPeer(rules, 0, 72)).toBe(true); // same column
    expect(isPeer(rules, 0, 20)).toBe(true); // same box (row 2, col 2)
    expect(isPeer(rules, 0, 40)).toBe(false);
    expect(isPeer(rules, 0, 0)).toBe(false);
  });
});

describe('findConflicts', () => {
  it('allows a repeated digit up to its frequency', () => {
    const values = new Array(81).fill(0);
    values[0] = 1;
    values[1] = 1;
    expect(findConflicts(rules, values).size).toBe(0);
  });

  it('flags a digit over its frequency', () => {
    const values = new Array(81).fill(0);
    values[0] = 3;
    values[5] = 3;
    expect([...findConflicts(rules, values)].sort()).toEqual([0, 5]);

    values[0] = 1;
    values[1] = 1;
    values[2] = 1;
    expect(findConflicts(rules, values).has(2)).toBe(true);
  });

  it('accepts every bundled engine solution', () => {
    for (const puzzle of bundledPuzzles) {
      expect(findConflicts(rules, puzzle.solution!).size, puzzle.name).toBe(0);
      expect(isSolved(rules, puzzle.solution!, null), puzzle.name).toBe(true);
    }
  });
});

describe('remainingCounts', () => {
  it('counts 18 of each doubled digit and 9 of the others on an empty board', () => {
    const remaining = remainingCounts(rules, new Array(81).fill(0));
    expect(remaining.get(1)).toBe(18);
    expect(remaining.get(5)).toBe(18);
    expect(remaining.get(9)).toBe(9);
    expect(remaining.has(7)).toBe(false);
  });
});
