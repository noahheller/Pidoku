import { describe, expect, it } from 'vitest';
import easyJson from '../puzzles/03-easy.json';
import { bundledPuzzles } from '../puzzles';
import { parsePuzzleJson, PuzzleFormatError } from './puzzleFile';

const fallback = { name: 'Test' };
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

describe('parsePuzzleJson', () => {
  it('reads the engine Main output format', () => {
    const puzzle = parsePuzzleJson(easyJson, fallback);
    expect(puzzle.size).toBe(9);
    expect(puzzle.givens).toHaveLength(81);
    expect(puzzle.solution).toHaveLength(81);
    expect(puzzle.score).toBe(easyJson.solveTrace.totalScore);
    expect(puzzle.hardestStrategy).toBe('HIDDEN_SINGLE');
    expect(puzzle.id).toMatch(/^custom-/);
  });

  it('loads all bundled puzzles with names from their file names', () => {
    expect(bundledPuzzles.map((p) => p.name)).toEqual(['Trivial', 'Relaxing', 'Easy', 'Medium', 'Hard', 'Fiendish']);
  });

  it('gives the same puzzle the same id so progress survives reloading it', () => {
    expect(parsePuzzleJson(easyJson, fallback).id).toBe(parsePuzzleJson(clone(easyJson), fallback).id);
  });

  it('accepts a puzzle without a solution', () => {
    const puzzle = parsePuzzleJson({ puzzle: easyJson.puzzle }, fallback);
    expect(puzzle.solution).toBeNull();
    expect(puzzle.score).toBeNull();
  });

  it('prefers explicit name and difficulty fields', () => {
    const puzzle = parsePuzzleJson({ ...easyJson, name: 'Level 1', difficulty: 'Easy' }, fallback);
    expect(puzzle.name).toBe('Level 1');
    expect(puzzle.difficulty).toBe('Easy');
  });

  const rejects: [string, unknown, RegExp][] = [
    ['non-objects', [1, 2, 3], /JSON object/],
    ['a missing puzzle', { solution: easyJson.solution }, /"puzzle"/],
    ['ragged rows', { puzzle: [[0, 0], [0]] }, /row 2/],
    ['non-square sizes', { puzzle: [[0, 0], [0, 0]] }, /perfect square/],
    ['digits that are not in pi', { puzzle: easyJson.puzzle.map((row, r) => (r === 0 ? [7, ...row.slice(1)] : row)) }, /7 at row 1, column 1/],
  ];
  it.each(rejects)('rejects %s', (_label, input, message) => {
    expect(() => parsePuzzleJson(input, fallback)).toThrow(PuzzleFormatError);
    expect(() => parsePuzzleJson(input, fallback)).toThrow(message);
  });

  it('rejects givens that break the rules', () => {
    const json = clone(easyJson);
    // Row 0 already has a 4 at column 2; a second 4 breaks the row.
    json.puzzle[0][0] = 4;
    expect(() => parsePuzzleJson({ puzzle: json.puzzle }, fallback)).toThrow(/breaks the Pidoku rules/);
  });

  it('rejects a solution that disagrees with the givens', () => {
    const json = clone(easyJson);
    const solution = json.solution;
    // Swap two different digits within the first row: still a valid row, but no longer matches a given.
    const a = solution[0].indexOf(json.puzzle[0][2]);
    const b = solution[0].findIndex((v: number) => v !== solution[0][a]);
    [solution[0][a], solution[0][b]] = [solution[0][b], solution[0][a]];
    expect(() => parsePuzzleJson(json, fallback)).toThrow(PuzzleFormatError);
  });
});
