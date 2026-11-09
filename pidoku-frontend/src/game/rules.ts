/**
 * Pidoku rules, mirroring pidoku-engine's PidokuRules: every row, column and box
 * contains the first `size` digits of pi, so some digits repeat and some never appear.
 * Grids are flat arrays of length size*size, indexed row-major. 0 means empty.
 */

export const EMPTY = 0;

/** Enough digits for every supported board size (up to 25; pi's first 0 is its 33rd digit). */
export const PI_DIGITS = [3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5, 8, 9, 7, 9, 3, 2, 3, 8, 4, 6, 2, 6, 4, 3];

export interface Rules {
  size: number;
  boxDim: number;
  /** Distinct playable digits, ascending. */
  digits: number[];
  /** frequency[d] = how many times digit d appears in each row, column and box. */
  frequency: number[];
  /** units[i] = the row, column and box (as cell-index lists) that contain cell i. */
  unitsOf: number[][][];
  /** All rows, columns and boxes. */
  units: number[][];
}

const cache = new Map<number, Rules>();

export function rulesFor(size: number): Rules {
  const cached = cache.get(size);
  if (cached) return cached;

  const boxDim = Math.round(Math.sqrt(size));
  if (size < 1 || boxDim * boxDim !== size || size > PI_DIGITS.length) {
    throw new Error(`Unsupported board size ${size}`);
  }

  const frequency = new Array<number>(10).fill(0);
  for (const d of PI_DIGITS.slice(0, size)) frequency[d]++;
  const digits = frequency.flatMap((count, d) => (count > 0 ? [d] : []));

  const rows: number[][] = [];
  const cols: number[][] = [];
  const boxes: number[][] = [];
  for (let u = 0; u < size; u++) {
    rows.push([]);
    cols.push([]);
    boxes.push([]);
  }
  for (let i = 0; i < size * size; i++) {
    const r = Math.floor(i / size);
    const c = i % size;
    rows[r].push(i);
    cols[c].push(i);
    boxes[Math.floor(r / boxDim) * boxDim + Math.floor(c / boxDim)].push(i);
  }

  const unitsOf: number[][][] = [];
  for (let i = 0; i < size * size; i++) {
    const r = Math.floor(i / size);
    const c = i % size;
    unitsOf.push([rows[r], cols[c], boxes[Math.floor(r / boxDim) * boxDim + Math.floor(c / boxDim)]]);
  }

  const rules: Rules = { size, boxDim, digits, frequency, unitsOf, units: [...rows, ...cols, ...boxes] };
  cache.set(size, rules);
  return rules;
}

export function isPlayable(rules: Rules, digit: number): boolean {
  return Number.isInteger(digit) && digit >= 0 && digit < rules.frequency.length && rules.frequency[digit] > 0;
}

export function rowOf(rules: Rules, i: number): number {
  return Math.floor(i / rules.size);
}

export function colOf(rules: Rules, i: number): number {
  return i % rules.size;
}

export function boxOf(rules: Rules, i: number): number {
  const { boxDim } = rules;
  return Math.floor(rowOf(rules, i) / boxDim) * boxDim + Math.floor(colOf(rules, i) / boxDim);
}

/** True if cells a and b share a row, column or box (and are different cells). */
export function isPeer(rules: Rules, a: number, b: number): boolean {
  if (a === b) return false;
  return rowOf(rules, a) === rowOf(rules, b) || colOf(rules, a) === colOf(rules, b) || boxOf(rules, a) === boxOf(rules, b);
}

function countIn(values: readonly number[], unit: readonly number[], digit: number): number {
  let n = 0;
  for (const i of unit) if (values[i] === digit) n++;
  return n;
}

/**
 * Cells whose digit appears in some row, column or box more often than pi allows.
 * A full grid with no conflicts is a valid solution, since each unit's frequencies sum to `size`.
 */
export function findConflicts(rules: Rules, values: readonly number[]): Set<number> {
  const conflicts = new Set<number>();
  for (const unit of rules.units) {
    const counts = new Array<number>(10).fill(0);
    for (const i of unit) if (values[i] !== EMPTY) counts[values[i]]++;
    for (const i of unit) {
      const d = values[i];
      if (d !== EMPTY && counts[d] > rules.frequency[d]) conflicts.add(i);
    }
  }
  return conflicts;
}

/** How many more of each digit the whole board still needs. */
export function remainingCounts(rules: Rules, values: readonly number[]): Map<number, number> {
  const remaining = new Map<number, number>();
  for (const d of rules.digits) remaining.set(d, rules.frequency[d] * rules.size);
  for (const v of values) {
    if (v !== EMPTY && remaining.has(v)) remaining.set(v, remaining.get(v)! - 1);
  }
  return remaining;
}

/** A unit is "full" for a digit once it holds as many copies as pi allows. */
export function unitIsFullFor(rules: Rules, values: readonly number[], unit: readonly number[], digit: number): boolean {
  return countIn(values, unit, digit) >= rules.frequency[digit];
}

export function isSolved(rules: Rules, values: readonly number[], solution: readonly number[] | null): boolean {
  if (values.some((v) => v === EMPTY)) return false;
  if (solution) return values.every((v, i) => v === solution[i]);
  return findConflicts(rules, values).size === 0;
}
