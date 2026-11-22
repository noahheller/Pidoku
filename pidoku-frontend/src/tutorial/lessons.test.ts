import { describe, expect, it } from 'vitest';
import { gameReducer, newGame, type GameAction, type GameState } from '../game/game';
import { EMPTY } from '../game/rules';
import { isDone, lessons, nextStep, noteCells, steps, tutorialPuzzle } from './lessons';

const play = (state: GameState, ...actions: GameAction[]) => actions.reduce(gameReducer, state);

describe('the tutorial', () => {
  it('can be played through as the lessons say, each finishing in turn', () => {
    let state = newGame(tutorialPuzzle);
    for (const lesson of lessons) {
      expect(isDone(lesson, state)).toBe(false);
      switch (lesson.kind) {
        case 'info':
          continue;
        case 'place':
          state = play(state, { type: 'select', index: lesson.step.cell! }, { type: 'input', digit: lesson.step.digit });
          break;
        case 'notes':
          state = play(state, { type: 'toggleNotesMode' });
          for (const i of lesson.cells) state = play(state, { type: 'select', index: i }, { type: 'input', digit: lesson.digit });
          state = play(state, { type: 'toggleNotesMode' });
          break;
        case 'finish':
          for (let step = nextStep(state.values); step; step = nextStep(state.values)) {
            state = play(state, { type: 'select', index: step.cell! }, { type: 'input', digit: step.digit });
          }
          break;
      }
      expect(isDone(lesson, state)).toBe(true);
    }
    expect(state.completed).toBe(true);
    expect(state.mistakes).toBe(0);
  });

  it('pencils into two cells, and placing the digit in one clears the other', () => {
    expect(noteCells).toHaveLength(2);
    const notes = lessons.find((l) => l.kind === 'notes')!;
    const place = lessons[lessons.indexOf(notes) + 1];
    if (notes.kind !== 'notes' || place.kind !== 'place') throw new Error('the notes lesson should come before a placement');
    expect(noteCells).toContain(place.step.cell);

    let state = newGame(tutorialPuzzle);
    for (const s of steps.filter((s) => s.guided && s !== place.step)) state = play(state, { type: 'select', index: s.cell! }, { type: 'input', digit: s.digit });
    state = play(state, { type: 'toggleNotesMode' });
    for (const i of noteCells) state = play(state, { type: 'select', index: i }, { type: 'input', digit: notes.digit });
    state = play(state, { type: 'toggleNotesMode' }, { type: 'select', index: place.step.cell! }, { type: 'input', digit: place.step.digit });

    const other = noteCells.find((i) => i !== place.step.cell)!;
    expect(state.values[other]).toBe(EMPTY);
    expect(state.notes[other] & (1 << notes.digit)).toBe(0);
  });

  it("solves to the puzzle's solution", () => {
    const values = [...tutorialPuzzle.givens];
    for (const s of steps) if (s.cell !== null) values[s.cell] = s.digit;
    expect(values).toEqual(tutorialPuzzle.solution);
  });
});
