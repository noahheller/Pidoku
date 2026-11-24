import { describe, expect, it } from 'vitest';
import { bundledPuzzles } from '../puzzles';
import { gameReducer, newGame, toSaved, type GameAction, type GameState } from './game';
import type { PuzzleDef } from './puzzleFile';
import { candidateMask } from './rules';

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

  it('restarting clears the board, the time and the mistakes', () => {
    const s = play(newGame(easy), { type: 'select', index: firstEmpty }, { type: 'input', digit: wrongDigit }, { type: 'tick', ms: 5000 }, { type: 'restart' });
    expect(s.values).toEqual(easy.givens);
    expect(s.elapsedMs).toBe(0);
    expect(s.mistakes).toBe(0);
  });

  it('restarting a daily clears the board but keeps its time and mistakes', () => {
    const daily = { ...easy, dailyDate: '2026-11-17' };
    const s = play(newGame(daily), { type: 'select', index: firstEmpty }, { type: 'input', digit: wrongDigit }, { type: 'tick', ms: 5000 }, { type: 'restart' });
    expect(s.values).toEqual(daily.givens);
    expect(s.elapsedMs).toBe(5000);
    expect(s.mistakes).toBe(1);
  });

  it('clears a wrong digit when it is entered again', () => {
    const s = play(newGame(easy), { type: 'select', index: firstEmpty }, { type: 'input', digit: wrongDigit }, { type: 'input', digit: wrongDigit });
    expect(s.values[firstEmpty]).toBe(0);
  });

  it('locks a correct digit: erasing, retyping or overwriting it changes nothing', () => {
    const placed = play(newGame(easy), { type: 'select', index: firstEmpty }, { type: 'input', digit: answer });
    expect(play(placed, { type: 'erase' })).toBe(placed);
    expect(play(placed, { type: 'input', digit: answer })).toBe(placed);
    expect(play(placed, { type: 'input', digit: wrongDigit })).toBe(placed);
    expect(play(placed, { type: 'input', digit: wrongDigit, asNote: true })).toBe(placed);
    // Undo can still take it back.
    expect(play(placed, { type: 'undo' }).values[firstEmpty]).toBe(0);
  });

  it('without a solution, a placed digit can still be cleared', () => {
    const noSolution: PuzzleDef = { ...easy, id: 'no-solution', solution: null };
    const s = play(newGame(noSolution), { type: 'select', index: firstEmpty }, { type: 'input', digit: answer }, { type: 'erase' });
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

  it('prunes peer notes only when the placed digit is correct', () => {
    // Blank board with a known solution; use a row-0 cell whose answer appears once per unit.
    const blank: PuzzleDef = { ...easy, id: 'blank-solved', givens: new Array(81).fill(0) };
    const k = [0, 1, 2, 3, 4, 5, 6, 7, 8].find((c) => ![1, 5].includes(blank.solution![c]))!;
    const peer = k === 0 ? 8 : 0;
    const right = blank.solution![k];
    const wrong = [2, 3, 4, 6, 9].find((d) => d !== right)!;
    const noted = play(newGame(blank), { type: 'select', index: peer }, { type: 'input', digit: right, asNote: true }, { type: 'input', digit: wrong, asNote: true });

    const afterWrong = play(noted, { type: 'select', index: k }, { type: 'input', digit: wrong });
    expect(afterWrong.notes[peer] & (1 << wrong)).not.toBe(0);

    const afterRight = play(noted, { type: 'select', index: k }, { type: 'input', digit: right });
    expect(afterRight.notes[peer] & (1 << right)).toBe(0);
  });

  it('keeps a cell\'s notes under a wrong digit and brings them back when it is removed', () => {
    const noted = play(newGame(easy), { type: 'select', index: firstEmpty }, { type: 'input', digit: wrongDigit, asNote: true }, { type: 'input', digit: answer, asNote: true });
    const kept = noted.notes[firstEmpty];

    const wrong = play(noted, { type: 'input', digit: wrongDigit });
    expect(wrong.notes[firstEmpty]).toBe(kept);

    const erased = play(wrong, { type: 'erase' });
    expect(erased.values[firstEmpty]).toBe(0);
    expect(erased.notes[firstEmpty]).toBe(kept);
    expect(play(erased, { type: 'erase' }).notes[firstEmpty]).toBe(0);

    expect(play(wrong, { type: 'input', digit: wrongDigit }).notes[firstEmpty]).toBe(kept);
    expect(play(wrong, { type: 'input', digit: answer }).notes[firstEmpty]).toBe(0);
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

describe('auto notes', () => {
  const expert = bundledPuzzles.find((p) => p.name === 'Expert')!;
  const empties = expert.givens.flatMap((v, i) => (v === 0 ? [i] : []));

  it('pencils every candidate into the empty cells, as one move Undo takes back', () => {
    const s = play(newGame(expert), { type: 'autoNotes' });
    expect(s.autoNotesUsed).toBe(true);
    for (const i of empties) {
      expect(s.notes[i]).toBe(candidateMask(s.rules, expert.givens, i));
      // The solution's digit is always among them.
      expect(s.notes[i] & (1 << expert.solution![i])).not.toBe(0);
    }
    expect(play(s, { type: 'undo' }).notes.every((n) => n === 0)).toBe(true);
  });

  it("keeps a cell's notes the player narrowed, only removing impossible ones", () => {
    const i = empties[0];
    const answerAt = expert.solution![i];
    const s = play(newGame(expert), { type: 'select', index: i }, { type: 'toggleNotesMode' }, { type: 'input', digit: answerAt }, { type: 'autoNotes' });
    expect(s.notes[i]).toBe(1 << answerAt);
  });

  it("isn't offered below Expert", () => {
    const start = newGame(easy);
    expect(play(start, { type: 'autoNotes' })).toBe(start);
  });
});

describe('load', () => {
  it("replaces the board with another device's, keeping the selection", () => {
    const i = firstEmpty;
    const values = [...easy.givens];
    values[i] = answer;
    const s = play(newGame(easy), { type: 'select', index: firstGiven }, { type: 'load', progress: { values, notes: Array(81).fill(0), mistakes: 2, elapsedMs: 40_000, completed: false } });
    expect(s.values[i]).toBe(answer);
    expect(s.mistakes).toBe(2);
    expect(s.elapsedMs).toBe(40_000);
    expect(s.selected).toBe(firstGiven);
  });

  it("ignores a board that doesn't fit the puzzle", () => {
    const start = newGame(easy);
    expect(play(start, { type: 'load', progress: { values: [1, 2], notes: [0, 0], mistakes: 0, elapsedMs: 0, completed: false } })).toBe(start);
  });
});
