import { describe, expect, it } from 'vitest';
import { bundledPuzzles } from '../puzzles';
import { gameReducer, newGame, toSaved, type GameAction, type GameState } from './game';
import type { PuzzleDef } from './puzzleFile';

const easy = bundledPuzzles.find((p) => p.name === 'Easy')!;
const firstEmpty = easy.givens.indexOf(0);
const firstGiven = easy.givens.findIndex((v) => v !== 0);
const answer = easy.solution![firstEmpty];
const wrongDigit = [1, 2, 3, 4, 5, 6, 9].find((d) => d !== answer)!;

function play(state: GameState, ...actions: GameAction[]): GameState {
  return actions.reduce(gameReducer, state);
}

describe('gameReducer', () => {
  it('places a digit in the selected empty cell', () => {
    const s = play(newGame(easy), { type: 'select', index: firstEmpty }, { type: 'input', digit: answer });
    expect(s.values[firstEmpty]).toBe(answer);
    expect(s.mistakes).toBe(0);
  });

  it('ignores input on givens, with nothing selected, and for digits not in pi', () => {
    const start = newGame(easy);
    expect(play(start, { type: 'input', digit: answer }).values).toEqual(start.values);
    expect(play(start, { type: 'select', index: firstGiven }, { type: 'input', digit: 2 }).values).toEqual(start.values);
    expect(play(start, { type: 'select', index: firstEmpty }, { type: 'input', digit: 7 }).values).toEqual(start.values);
  });

  it('counts wrong digits as mistakes', () => {
    const s = play(newGame(easy), { type: 'select', index: firstEmpty }, { type: 'input', digit: wrongDigit });
    expect(s.mistakes).toBe(1);
  });

  it('clears a cell when the same digit is entered again', () => {
    const s = play(newGame(easy), { type: 'select', index: firstEmpty }, { type: 'input', digit: answer }, { type: 'input', digit: answer });
    expect(s.values[firstEmpty]).toBe(0);
  });

  it('toggles notes in notes mode and with asNote', () => {
    let s = play(newGame(easy), { type: 'select', index: firstEmpty }, { type: 'toggleNotesMode' }, { type: 'input', digit: 3 }, { type: 'input', digit: 5 });
    expect(s.notes[firstEmpty]).toBe((1 << 3) | (1 << 5));
    expect(s.values[firstEmpty]).toBe(0);
    s = play(s, { type: 'toggleNotesMode' }, { type: 'input', digit: 3, asNote: true });
    expect(s.notes[firstEmpty]).toBe(1 << 5);
  });

  it('only prunes notes of a doubled digit once its unit holds both copies', () => {
    // Empty 9x9 board so we control every placement.
    const blank: PuzzleDef = { ...easy, id: 'blank', givens: new Array(81).fill(0), solution: null };
    const noteOne = (s: GameState, i: number) => play(s, { type: 'select', index: i }, { type: 'input', digit: 1, asNote: true });
    let s = [3, 4, 5].reduce(noteOne, newGame(blank));

    s = play(s, { type: 'select', index: 0 }, { type: 'input', digit: 1 });
    expect(s.notes[3] & (1 << 1)).not.toBe(0); // row 0 still needs another 1

    s = play(s, { type: 'select', index: 1 }, { type: 'input', digit: 1 });
    expect(s.notes[3] & (1 << 1)).toBe(0); // row 0 now has both 1s
    expect(s.notes[5] & (1 << 1)).toBe(0);
  });

  it('undoes and redoes', () => {
    const start = play(newGame(easy), { type: 'select', index: firstEmpty });
    const placed = play(start, { type: 'input', digit: answer });
    const undone = play(placed, { type: 'undo' });
    expect(undone.values).toEqual(start.values);
    expect(play(undone, { type: 'redo' }).values).toEqual(placed.values);
  });

  it('moves the selection with wraparound', () => {
    const s = play(newGame(easy), { type: 'select', index: 0 }, { type: 'move', dRow: -1, dCol: -1 });
    expect(s.selected).toBe(80);
  });

  it('completes when every cell matches the solution, then locks the board', () => {
    let s = newGame(easy);
    easy.givens.forEach((g, i) => {
      if (g === 0) s = play(s, { type: 'select', index: i }, { type: 'input', digit: easy.solution![i] });
    });
    expect(s.completed).toBe(true);
    expect(play(s, { type: 'erase' }).values).toEqual(s.values);
    expect(play(s, { type: 'tick', ms: 1000 }).elapsedMs).toBe(s.elapsedMs);
  });

  it('completes a puzzle without a solution once the grid is full and valid', () => {
    const noSolution: PuzzleDef = { ...easy, id: 'no-solution', solution: null };
    let s = newGame(noSolution);
    easy.givens.forEach((g, i) => {
      if (g === 0) s = play(s, { type: 'select', index: i }, { type: 'input', digit: easy.solution![i] });
    });
    expect(s.completed).toBe(true);
    expect(s.mistakes).toBe(0);
  });

  it('restores saved progress, but ignores saves that do not fit the puzzle', () => {
    const s = play(newGame(easy), { type: 'select', index: firstEmpty }, { type: 'input', digit: answer }, { type: 'tick', ms: 5000 });
    const restored = newGame(easy, toSaved(s));
    expect(restored.values).toEqual(s.values);
    expect(restored.elapsedMs).toBe(5000);

    const tampered = { ...toSaved(s), values: s.values.map((v, i) => (i === firstGiven ? 0 : v)) };
    expect(newGame(easy, tampered).values).toEqual(easy.givens);
  });
});
