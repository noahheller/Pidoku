import type { BoardHighlight } from '../components/Board';
import type { GameState } from '../game/game';
import { cellsOfUnits } from '../game/hints';
import type { PuzzleDef } from '../game/puzzleFile';
import { candidateMask, EMPTY, PI_DIGITS, rulesFor } from '../game/rules';
import tutorial from './tutorial.json';

/**
 * The tutorial: a nearly full board and the lessons that walk a new player through it. The board
 * and every solving step's wording come from the engine ("Explain a move", exported to
 * tutorial.json by `./gradlew :pidoku-engine:exportTutorial`); the lessons around them, about the
 * rules and the controls, are written here.
 */

/** One solving step as the engine explains it (pidoku-engine's Explanation). Cells are row-major indices. */
export interface ExplainedStep {
  /** One of the tutorial's scripted steps, rather than the solver's own path through the rest. */
  guided: boolean;
  strategy: string;
  /** The cell a digit goes in; null for a step that only clears candidates. */
  cell: number | null;
  digit: number;
  units: { type: 'row' | 'column' | 'box'; index: number }[];
  pattern: number[];
  blockers: number[];
  eliminations: { cell: number; digit: number }[];
  text: string;
}

export const steps = tutorial.steps as ExplainedStep[];
const guided = steps.filter((s) => s.guided);

export const tutorialPuzzle: PuzzleDef = {
  id: 'tutorial',
  name: 'How to play',
  difficulty: null,
  size: tutorial.size,
  givens: tutorial.givens,
  solution: tutorial.solution,
  score: null,
  hardestStrategy: null,
};

const rules = rulesFor(tutorialPuzzle.size);

/** Every cell of the units a step reasons about. */
export function unitCells(step: Pick<ExplainedStep, 'units'>): number[] {
  return cellsOfUnits(rules, step.units);
}

/** What the board points at for a step. */
export function highlightFor(step: ExplainedStep): BoardHighlight {
  return { area: unitCells(step), pattern: step.pattern, blockers: step.blockers, target: step.cell };
}

export type Lesson =
  /** Read it and press Next. */
  | { kind: 'info'; title: string; body: string; highlight: BoardHighlight | null }
  /** Done once `digit` is in `step.cell`. */
  | { kind: 'place'; title: string; body: string; instruction: string; step: ExplainedStep }
  /** Done once every one of `cells` has `digit` pencilled in. */
  | { kind: 'notes'; title: string; body: string; cells: number[]; digit: number; highlight: BoardHighlight }
  /** Done once the board is solved. */
  | { kind: 'finish'; title: string; body: string };

const [single, secondFive, secondOne] = guided;

/** The board just before the notes lesson: the givens plus the steps before it. */
function boardBefore(step: ExplainedStep): number[] {
  const values = [...tutorialPuzzle.givens];
  for (const s of steps) {
    if (s === step) break;
    if (s.cell !== null) values[s.cell] = s.digit;
  }
  return values;
}

/**
 * The notes lesson: the empty cells in the row of the third guided step's cell that could hold its
 * digit. The step then fills that row's last copy, and the note beside it clears by itself.
 */
const noteRow = Math.floor(secondOne.cell! / rules.size);
const before = boardBefore(secondOne);
export const noteCells = rules.units[noteRow].filter((i) => before[i] === EMPTY && (candidateMask(rules, before, i) & (1 << secondOne.digit)) !== 0);

const firstRow = rules.units[0];
const repeated = rules.digits.filter((d) => rules.frequency[d] > 1);

export const lessons: Lesson[] = [
  {
    kind: 'info',
    title: 'Sudoku with the digits of π',
    body: `Every row, column and box contains the first nine digits of π: ${PI_DIGITS.slice(0, 9).join(' ')}. \n That means, in each section there are two 1s and two 5s, but no 7s or 8s.\n Row 1 has its 1s and 5s marked.`,
    highlight: { area: firstRow, pattern: firstRow.filter((i) => repeated.includes(tutorialPuzzle.givens[i])), blockers: [], target: null },
  },
  {
    kind: 'place',
    title: 'Start with a single',
    body: single.text,
    instruction: `Tap the glowing cell, then ${single.digit}. The ringed ${single.digit}s are what rule out the box's other cells.`,
    step: single,
  },
  {
    kind: 'place',
    title: 'The twist: a second 5',
    body: secondFive.text,
    instruction: `In Sudoku a row with a ${secondFive.digit} is done with ${secondFive.digit}s. Here it needs two, so place the second.`,
    step: secondFive,
  },
  {
    kind: 'notes',
    title: 'Pencil in notes',
    body: `Row ${noteRow + 1} has one ${secondOne.digit} and needs another, and it could go in either highlighted cell. Turn Notes on, then pencil a ${secondOne.digit} into both.`,
    cells: noteCells,
    digit: secondOne.digit,
    highlight: { area: rules.units[noteRow], pattern: noteCells, blockers: [], target: null },
  },
  {
    kind: 'place',
    title: 'Watch your notes',
    body: secondOne.text,
    instruction: `Turn Notes off and write the ${secondOne.digit}, then look at the note beside it.`,
    step: secondOne,
  },
  {
    kind: 'info',
    title: 'Notes clear themselves',
    body: `The ${secondOne.digit} you pencilled beside it is gone: row ${noteRow + 1} now has both its ${secondOne.digit}s, so no other cell in it can be a ${secondOne.digit}. With only one ${secondOne.digit} in a row, the note stays, since the row can still take another.`,
    highlight: { area: rules.units[noteRow], pattern: noteCells.filter((i) => i !== secondOne.cell), blockers: [], target: null },
  },
  {
    kind: 'info',
    title: 'Mistakes and undo',
    body: 'A wrong digit shows in red and counts as a mistake, which shows next to your time when you share. Erase clears the selected cell, and Undo takes back your last move.',
    highlight: null,
  },
  {
    kind: 'finish',
    title: 'Finish it off',
    body: 'Fill in the last few cells. Stuck? Show me points at the next step.',
  },
];

/** Whether a lesson's task is done on this board (info lessons wait for Next). */
export function isDone(lesson: Lesson, state: GameState): boolean {
  switch (lesson.kind) {
    case 'info':
      return false;
    case 'place':
      return state.values[lesson.step.cell!] === lesson.step.digit;
    case 'notes':
      return lesson.cells.every((i) => (state.notes[i] & (1 << lesson.digit)) !== 0);
    case 'finish':
      return state.completed;
  }
}

/** The next step to show for "Show me": the first one whose cell is still empty. */
export function nextStep(values: readonly number[]): ExplainedStep | null {
  return steps.find((s) => s.cell !== null && values[s.cell] === EMPTY) ?? null;
}
