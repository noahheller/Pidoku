import { describe, expect, it } from 'vitest';
import type { Hint } from '../api/client';
import { cellsOfUnits, hintHighlight, hintText, hintUsed, whereToLook } from './hints';
import { rulesFor } from './rules';

const rules = rulesFor(9);
const base = { units: [{ type: 'row' as const, index: 3 }], hintsUsed: 1, hintPenaltyMs: 10_000 };

describe('cellsOfUnits', () => {
  it('finds the cells of rows, columns and boxes without repeats', () => {
    expect(cellsOfUnits(rules, [{ type: 'row', index: 0 }])).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8]);
    expect(cellsOfUnits(rules, [{ type: 'column', index: 2 }])).toEqual([2, 11, 20, 29, 38, 47, 56, 65, 74]);
    expect(cellsOfUnits(rules, [{ type: 'box', index: 4 }])).toEqual([30, 31, 32, 39, 40, 41, 48, 49, 50]);
    expect(cellsOfUnits(rules, [{ type: 'row', index: 0 }, { type: 'box', index: 0 }])).toHaveLength(9 + 6);
  });
});

describe('whereToLook', () => {
  it('names the units, counting from 1', () => {
    expect(whereToLook([{ type: 'row', index: 3 }])).toBe('Look at row 4.');
    expect(whereToLook([{ type: 'row', index: 1 }, { type: 'row', index: 6 }])).toBe('Look at rows 2 and 7.');
    expect(whereToLook([{ type: 'box', index: 4 }, { type: 'row', index: 2 }])).toBe('Look at row 3 and box 5.');
    expect(whereToLook([{ type: 'box', index: 0 }, { type: 'box', index: 1 }, { type: 'column', index: 8 }])).toBe('Look at column 9 and boxes 1 and 2.');
  });
});

describe('hintHighlight', () => {
  it('shows more of the board at each step', () => {
    const where: Hint = { ...base, step: 1 };
    const what: Hint = { ...base, step: 2, strategy: 'HIDDEN_SINGLE_LINE', pattern: [30], blockers: [5] };
    const answer: Hint = { ...what, step: 3, cell: 30, digit: 5, text: 'Only cell left.' };

    expect(hintHighlight(rules, where)).toEqual({ area: rules.units[3], pattern: [], blockers: [], target: null });
    expect(hintHighlight(rules, what)).toEqual({ area: rules.units[3], pattern: [30], blockers: [5], target: null });
    expect(hintHighlight(rules, answer).target).toBe(30);
  });

  it('has no target for a step that only clears candidates', () => {
    expect(hintHighlight(rules, { ...base, step: 3, strategy: 'X_WING', pattern: [1, 2], text: 'x' }).target).toBeNull();
  });
});

describe('hintText', () => {
  it('says where to look, then what to look for with its name, then the answer', () => {
    expect(hintText({ ...base, step: 1 })).toEqual({ text: 'Look at row 4.', name: null, explanation: null });

    const what = hintText({ ...base, step: 2, strategy: 'HIDDEN_SINGLE_LINE', pattern: [], blockers: [] });
    expect(what.name).toBe('Hidden single (row or column)');
    expect(what.text).toMatch(/^In this row or column/);
    expect(what.explanation).toBeTruthy();

    expect(hintText({ ...base, step: 3, strategy: 'HIDDEN_SINGLE_LINE', text: 'Row 4 needs a 5 here.' }).text).toBe('Row 4 needs a 5 here.');
  });
});

describe('hintUsed', () => {
  const solution = Array.from({ length: 81 }, (_, i) => (i % 9) + 1);
  const before = new Array<number>(81).fill(0);
  const hint: Hint = { ...base, step: 1 }; // row 4: cells 27 to 35

  it('is used once a correct digit goes anywhere in the hinted area', () => {
    const values = [...before];
    values[30] = solution[30];
    expect(hintUsed(rules, hint, before, values, solution)).toBe(true);
  });

  it("isn't used by a wrong digit, a digit outside the area, or one that was already there", () => {
    const wrong = [...before];
    wrong[30] = solution[30] + 1;
    expect(hintUsed(rules, hint, before, wrong, solution)).toBe(false);

    const elsewhere = [...before];
    elsewhere[0] = solution[0];
    expect(hintUsed(rules, hint, before, elsewhere, solution)).toBe(false);

    const already = [...before];
    already[30] = solution[30];
    expect(hintUsed(rules, hint, already, already, solution)).toBe(false);
  });
});
