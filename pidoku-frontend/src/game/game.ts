import type { PuzzleDef } from './puzzleFile';
import { EMPTY, isPlayable, isSolved, rulesFor, unitIsFullFor, type Rules } from './rules';

/** notes[i] is a bitmask: bit (1 << d) set means digit d is pencilled into cell i. */
export interface GameState {
  puzzle: PuzzleDef;
  rules: Rules;
  values: number[];
  notes: number[];
  selected: number | null;
  notesMode: boolean;
  undoStack: Snapshot[];
  redoStack: Snapshot[];
  /** Wrong digits entered (only counted when the puzzle has a solution). */
  mistakes: number;
  elapsedMs: number;
  completed: boolean;
}

interface Snapshot {
  values: number[];
  notes: number[];
}

/** The part of a game worth keeping between visits. */
export interface SavedProgress {
  values: number[];
  notes: number[];
  mistakes: number;
  elapsedMs: number;
  completed: boolean;
}

export type GameAction =
  | { type: 'select'; index: number | null }
  | { type: 'move'; dRow: number; dCol: number }
  | { type: 'input'; digit: number; asNote?: boolean }
  | { type: 'erase' }
  | { type: 'toggleNotesMode' }
  | { type: 'undo' }
  | { type: 'redo' }
  | { type: 'tick'; ms: number }
  | { type: 'restart' };

const MAX_HISTORY = 500;

export function newGame(puzzle: PuzzleDef, saved?: SavedProgress | null): GameState {
  const rules = rulesFor(puzzle.size);
  const cells = puzzle.size * puzzle.size;
  const restored = saved && isCompatible(puzzle, saved) ? saved : null;
  return {
    puzzle,
    rules,
    values: restored ? [...restored.values] : [...puzzle.givens],
    notes: restored ? [...restored.notes] : new Array<number>(cells).fill(0),
    selected: null,
    notesMode: false,
    undoStack: [],
    redoStack: [],
    mistakes: restored?.mistakes ?? 0,
    elapsedMs: restored?.elapsedMs ?? 0,
    completed: restored?.completed ?? false,
  };
}

export function toSaved(state: GameState): SavedProgress {
  const { values, notes, mistakes, elapsedMs, completed } = state;
  return { values, notes, mistakes, elapsedMs, completed };
}

export function isGiven(state: GameState, index: number): boolean {
  return state.puzzle.givens[index] !== EMPTY;
}

export function isMistake(state: GameState, index: number): boolean {
  const { solution } = state.puzzle;
  const v = state.values[index];
  return solution !== null && v !== EMPTY && v !== solution[index];
}

export function gameReducer(state: GameState, action: GameAction): GameState {
  switch (action.type) {
    case 'select':
      return { ...state, selected: action.index };

    case 'move': {
      const { size } = state.rules;
      if (state.selected === null) return { ...state, selected: 0 };
      const row = (Math.floor(state.selected / size) + action.dRow + size) % size;
      const col = ((state.selected % size) + action.dCol + size) % size;
      return { ...state, selected: row * size + col };
    }

    case 'toggleNotesMode':
      return { ...state, notesMode: !state.notesMode };

    case 'tick':
      return state.completed ? state : { ...state, elapsedMs: state.elapsedMs + action.ms };

    case 'input': {
      const i = state.selected;
      if (i === null || state.completed || isGiven(state, i) || !isPlayable(state.rules, action.digit)) return state;
      const asNote = action.asNote ?? state.notesMode;
      return asNote ? toggleNote(state, i, action.digit) : placeDigit(state, i, action.digit);
    }

    case 'erase': {
      const i = state.selected;
      if (i === null || state.completed || isGiven(state, i)) return state;
      if (state.values[i] === EMPTY && state.notes[i] === 0) return state;
      return commit(state, replaceAt(state.values, i, EMPTY), replaceAt(state.notes, i, 0));
    }

    case 'undo': {
      const previous = state.undoStack.at(-1);
      if (!previous || state.completed) return state;
      return {
        ...state,
        ...previous,
        undoStack: state.undoStack.slice(0, -1),
        redoStack: [...state.redoStack, snapshot(state)],
      };
    }

    case 'redo': {
      const next = state.redoStack.at(-1);
      if (!next || state.completed) return state;
      return withCompletion({
        ...state,
        ...next,
        redoStack: state.redoStack.slice(0, -1),
        undoStack: [...state.undoStack, snapshot(state)],
      });
    }

    case 'restart':
      return { ...newGame(state.puzzle), selected: state.selected };
  }
}

function toggleNote(state: GameState, i: number, digit: number): GameState {
  if (state.values[i] !== EMPTY) return state;
  return commit(state, state.values, replaceAt(state.notes, i, state.notes[i] ^ (1 << digit)));
}

function placeDigit(state: GameState, i: number, digit: number): GameState {
  // Entering the digit already in the cell clears it, like most Sudoku apps.
  const newValue = state.values[i] === digit ? EMPTY : digit;
  const values = replaceAt(state.values, i, newValue);
  const notes = replaceAt(state.notes, i, 0);

  if (newValue !== EMPTY) {
    // Pidoku twist: a digit only rules itself out of a unit once the unit holds
    // as many copies as pi allows (two for 1 and 5 on a 9x9 board).
    for (const unit of state.rules.unitsOf[i]) {
      if (!unitIsFullFor(state.rules, values, unit, newValue)) continue;
      for (const j of unit) {
        if (values[j] === EMPTY) notes[j] &= ~(1 << newValue);
      }
    }
  }

  const next = commit(state, values, notes);
  const wrong = newValue !== EMPTY && state.puzzle.solution !== null && newValue !== state.puzzle.solution[i];
  return wrong ? { ...next, mistakes: next.mistakes + 1 } : next;
}

function commit(state: GameState, values: number[], notes: number[]): GameState {
  return withCompletion({
    ...state,
    values,
    notes,
    undoStack: [...state.undoStack, snapshot(state)].slice(-MAX_HISTORY),
    redoStack: [],
  });
}

function withCompletion(state: GameState): GameState {
  const completed = isSolved(state.rules, state.values, state.puzzle.solution);
  return completed ? { ...state, completed, notesMode: false } : { ...state, completed };
}

function snapshot(state: GameState): Snapshot {
  return { values: state.values, notes: state.notes };
}

function replaceAt(array: number[], i: number, value: number): number[] {
  const copy = [...array];
  copy[i] = value;
  return copy;
}

/** Saved progress must match this puzzle's shape and keep every given in place. */
function isCompatible(puzzle: PuzzleDef, saved: SavedProgress): boolean {
  const cells = puzzle.size * puzzle.size;
  return (
    Array.isArray(saved.values) &&
    Array.isArray(saved.notes) &&
    saved.values.length === cells &&
    saved.notes.length === cells &&
    puzzle.givens.every((g, i) => g === EMPTY || saved.values[i] === g)
  );
}
