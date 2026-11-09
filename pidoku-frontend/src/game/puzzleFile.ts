import { EMPTY, findConflicts, isPlayable, rulesFor } from './rules';

/** A puzzle ready to play. Grids are flat, row-major arrays. */
export interface PuzzleDef {
  id: string;
  name: string;
  difficulty: string | null;
  size: number;
  givens: number[];
  /** Null when the source didn't include one (e.g. a future API campaign puzzle). */
  solution: number[] | null;
  score: number | null;
  hardestStrategy: string | null;
  /**
   * Average number of cells fillable with a single over the first 25 steps
   * (the engine's "dependency" metric). Lower means fewer ways forward at a time.
   * Optional because puzzles saved before this field existed don't have it.
   */
  dependency?: number | null;
}

export class PuzzleFormatError extends Error {}

/**
 * Accepts the JSON pidoku-engine's Main writes (a serialized PidokuPuzzle):
 *   { "puzzle": number[][], "solution"?: number[][], "solveTrace"?: { totalScore, hardestStrategy, dependency } }
 * plus optional "name" and "difficulty" fields.
 */
export function parsePuzzleJson(raw: unknown, fallback: { id?: string; name: string; difficulty?: string }): PuzzleDef {
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
    throw new PuzzleFormatError('Expected a JSON object with a "puzzle" field.');
  }
  const obj = raw as Record<string, unknown>;

  const givens2d = asGrid(obj.puzzle, 'puzzle');
  const size = givens2d.length;
  let rules;
  try {
    rules = rulesFor(size);
  } catch {
    throw new PuzzleFormatError(`"puzzle" is ${size}x${size}; size must be a perfect square up to 25.`);
  }
  const givens = givens2d.flat();
  givens.forEach((v, i) => {
    if (v !== EMPTY && !isPlayable(rules, v)) {
      throw new PuzzleFormatError(`"puzzle" has ${v} at ${cellName(i, size)}, which isn't a digit of this Pidoku.`);
    }
  });
  if (findConflicts(rules, givens).size > 0) {
    throw new PuzzleFormatError('"puzzle" breaks the Pidoku rules: a digit appears too often in a row, column or box.');
  }

  let solution: number[] | null = null;
  if (obj.solution !== undefined && obj.solution !== null) {
    const solution2d = asGrid(obj.solution, 'solution');
    if (solution2d.length !== size) {
      throw new PuzzleFormatError(`"solution" is ${solution2d.length}x${solution2d.length} but "puzzle" is ${size}x${size}.`);
    }
    solution = solution2d.flat();
    if (solution.some((v) => !isPlayable(rules, v)) || findConflicts(rules, solution).size > 0) {
      throw new PuzzleFormatError('"solution" is not a complete, valid Pidoku grid.');
    }
    givens.forEach((v, i) => {
      if (v !== EMPTY && v !== solution![i]) {
        throw new PuzzleFormatError(`"puzzle" and "solution" disagree at ${cellName(i, size)}.`);
      }
    });
  }

  const trace = typeof obj.solveTrace === 'object' && obj.solveTrace !== null ? (obj.solveTrace as Record<string, unknown>) : {};

  return {
    id: fallback.id ?? `custom-${hashGivens(givens)}`,
    name: typeof obj.name === 'string' && obj.name.trim() ? obj.name.trim() : fallback.name,
    difficulty: typeof obj.difficulty === 'string' ? obj.difficulty : (fallback.difficulty ?? null),
    size,
    givens,
    solution,
    score: typeof trace.totalScore === 'number' ? trace.totalScore : null,
    hardestStrategy: typeof trace.hardestStrategy === 'string' ? trace.hardestStrategy : null,
    dependency: typeof trace.dependency === 'number' && Number.isFinite(trace.dependency) ? trace.dependency : null,
  };
}

function asGrid(value: unknown, field: string): number[][] {
  if (!Array.isArray(value) || value.length === 0) {
    throw new PuzzleFormatError(`"${field}" must be a non-empty array of rows.`);
  }
  const size = value.length;
  return value.map((row, r) => {
    if (!Array.isArray(row) || row.length !== size) {
      throw new PuzzleFormatError(`"${field}" row ${r + 1} must have ${size} numbers.`);
    }
    return row.map((v, c) => {
      if (typeof v !== 'number' || !Number.isInteger(v)) {
        throw new PuzzleFormatError(`"${field}" has a non-integer at row ${r + 1}, column ${c + 1}.`);
      }
      return v;
    });
  });
}

function cellName(i: number, size: number): string {
  return `row ${Math.floor(i / size) + 1}, column ${(i % size) + 1}`;
}

/** Short stable hash of a grid, so the same puzzle keeps its saved progress (and a changed one doesn't). */
export function hashGivens(values: readonly number[]): string {
  let h = 0x811c9dc5;
  for (const v of values) {
    h ^= v;
    h = Math.imul(h, 0x01000193);
  }
  return (h >>> 0).toString(36);
}
