import type { PuzzleDef } from './puzzleFile';
import { candidateMask, EMPTY, isPlayable, isSolved, rulesFor, unitIsFullFor, type Rules } from './rules';

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
  /** Auto notes was pressed on this board (it changes solve times, so it's recorded). */
  autoNotesUsed: boolean;
  /** Hint steps revealed, as the server counted them. */
  hintsUsed: number;
  /** Time the server added for hints. */
  hintPenaltyMs: number;
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
  /** Missing from games saved before auto notes existed. */
  autoNotesUsed?: boolean;
  /** Missing from games saved before hints existed. */
  hintsUsed?: number;
  hintPenaltyMs?: number;
  /** When it was saved (Date.now()), to tell it from a newer copy saved on another device. Missing from older saves. */
  savedAt?: number;
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
  | { type: 'restart' }
  /** Pencils every candidate into the empty cells (Expert and Fiendish only, see `canAutoNote`). */
  | { type: 'autoNotes' }
  /** Replaces the board with one saved on another device. */
  | { type: 'load'; progress: SavedProgress }
  /** The server's hint totals for the attempt: after a hint, or when the attempt starts. */
  | { type: 'hinted'; hintsUsed: number; hintPenaltyMs: number };

/** Auto notes is only offered on the hardest puzzles; easier ones are meant to be solved without it. */
export function canAutoNote(puzzle: PuzzleDef): boolean {
  const difficulty = puzzle.difficulty?.toLowerCase();
  return difficulty === 'expert' || difficulty === 'fiendish';
}

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
    autoNotesUsed: restored?.autoNotesUsed ?? false,
    hintsUsed: restored?.hintsUsed ?? 0,
    hintPenaltyMs: restored?.hintPenaltyMs ?? 0,
  };
}

export function toSaved(state: GameState, now: number = Date.now()): SavedProgress {
  const { values, notes, mistakes, elapsedMs, completed, autoNotesUsed, hintsUsed, hintPenaltyMs } = state;
  return { values, notes, mistakes, elapsedMs, completed, autoNotesUsed, hintsUsed, hintPenaltyMs, savedAt: now };
}

export function isGiven(state: GameState, index: number): boolean {
  return state.puzzle.givens[index] !== EMPTY;
}

/**
 * A cell the player can't change: a given, or a digit that matches the solution. Correct digits
 * stay put so a slip can't undo progress (Undo can still take one back). Puzzles without a
 * solution can't tell, so only their givens are locked.
 */
export function isLocked(state: GameState, index: number): boolean {
  const { solution } = state.puzzle;
  return isGiven(state, index) || (solution !== null && state.values[index] !== EMPTY && state.values[index] === solution[index]);
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
      if (i === null || state.completed || isLocked(state, i) || !isPlayable(state.rules, action.digit)) return state;
      const asNote = action.asNote ?? state.notesMode;
      return asNote ? toggleNote(state, i, action.digit) : placeDigit(state, i, action.digit);
    }

    case 'erase': {
      const i = state.selected;
      if (i === null || state.completed || isLocked(state, i)) return state;
      // A filled cell may still hold notes hidden under a wrong digit: the first erase removes
      // just the digit so they reappear, and a second erase clears the notes.
      if (state.values[i] !== EMPTY) return commit(state, replaceAt(state.values, i, EMPTY), state.notes);
      if (state.notes[i] === 0) return state;
      return commit(state, state.values, replaceAt(state.notes, i, 0));
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

    case 'autoNotes':
      if (state.completed || !canAutoNote(state.puzzle)) return state;
      return { ...fillCandidates(state), autoNotesUsed: true };

    case 'load': {
      // A save that doesn't fit this puzzle leaves the board as it is. Hints are the server's
      // count, which the progress from another device doesn't carry, so they stay.
      if (!isCompatible(state.puzzle, action.progress)) return state;
      return {
        ...newGame(state.puzzle, action.progress),
        selected: state.selected,
        notesMode: state.notesMode,
        hintsUsed: state.hintsUsed,
        hintPenaltyMs: state.hintPenaltyMs,
      };
    }

    case 'hinted':
      return state.completed ? state : { ...state, hintsUsed: action.hintsUsed, hintPenaltyMs: action.hintPenaltyMs };

    case 'restart': {
      // A daily is ranked, so restarting one only clears the board: its time, mistakes and hints
      // carry on (the server keeps its clock running too).
      const fresh = { ...newGame(state.puzzle), selected: state.selected };
      return state.puzzle.dailyDate
        ? { ...fresh, elapsedMs: state.elapsedMs, mistakes: state.mistakes, hintsUsed: state.hintsUsed, hintPenaltyMs: state.hintPenaltyMs }
        : fresh;
    }
  }
}

/**
 * Auto notes: every empty cell with no notes gets all its candidates, and cells the player has
 * pencilled keep their notes minus any that are no longer possible, so digits they ruled out stay
 * ruled out. Wrong digits on the board don't count, so a mistake can't clear a true candidate.
 * One step on the undo stack.
 */
function fillCandidates(state: GameState): GameState {
  const { rules, values, puzzle } = state;
  const counted = puzzle.solution ? values.map((v, i) => (v !== EMPTY && v !== puzzle.solution![i] ? EMPTY : v)) : values;
  const notes = state.notes.map((n, i) => {
    if (values[i] !== EMPTY) return n;
    const candidates = candidateMask(rules, counted, i);
    return n === 0 ? candidates : n & candidates;
  });
  if (notes.every((n, i) => n === state.notes[i])) return state;
  return commit(state, values, notes);
}

function toggleNote(state: GameState, i: number, digit: number): GameState {
  if (state.values[i] !== EMPTY) return state;
  return commit(state, state.values, replaceAt(state.notes, i, state.notes[i] ^ (1 << digit)));
}

function placeDigit(state: GameState, i: number, digit: number): GameState {
  // Entering the digit already in the cell clears it, like most Sudoku apps.
  const newValue = state.values[i] === digit ? EMPTY : digit;
  const values = replaceAt(state.values, i, newValue);
  const wrong = newValue !== EMPTY && state.puzzle.solution !== null && newValue !== state.puzzle.solution[i];
  // A wrong digit keeps the cell's notes (hidden under it, back once it's removed) and leaves
  // peer notes alone, so a mistake doesn't wipe out pencil work.
  const notes = newValue === EMPTY || wrong ? [...state.notes] : replaceAt(state.notes, i, 0);

  if (newValue !== EMPTY && !wrong) {
    // Pidoku twist: a digit only rules itself out of a unit once the unit holds
    // as many copies as pi allows (two for 1 and 5 on a 9x9 board).
    for (const unit of state.rules.unitsOf[i]) {
      if (!unitIsFullFor(state.rules, values, unit, newValue)) continue;
      for (const j of unit) {
        // Filled cells too: a wrong digit may be hiding notes that would reappear stale.
        notes[j] &= ~(1 << newValue);
      }
    }
  }

  const next = commit(state, values, notes);
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
