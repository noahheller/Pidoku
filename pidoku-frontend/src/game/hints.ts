import type { Hint, UnitRef } from '../api/client';
import type { BoardHighlight } from '../components/Board';
import type { Rules } from './rules';
import { strategyInfo } from './strategies';

/**
 * Hints (milestone 6): the server's next move for the board, a step at a time. These work out what
 * the board shows and what the panel says for each step.
 */

/** Every cell of the given rows, columns and boxes (row-major indices). */
export function cellsOfUnits(rules: Rules, units: readonly UnitRef[]): number[] {
  const cells = new Set<number>();
  for (const unit of units) {
    const offset = unit.type === 'row' ? 0 : unit.type === 'column' ? rules.size : 2 * rules.size;
    for (const i of rules.units[offset + unit.index]) cells.add(i);
  }
  return [...cells];
}

/**
 * The player has acted on the hint: since it was given (`before`), a correct digit has gone into
 * one of the cells it points at. Any cell of its rows, columns or boxes counts, not just the one the
 * solver had in mind. Until then the hint stays up, so a wrong guess doesn't lose it.
 */
export function hintUsed(rules: Rules, hint: Hint, before: readonly number[], values: readonly number[], solution: readonly number[]): boolean {
  return cellsOfUnits(rules, hint.units).some((i) => before[i] === 0 && values[i] !== 0 && values[i] === solution[i]);
}

/** What the board points at: the units, then the pattern and blockers, then the cell to fill. */
export function hintHighlight(rules: Rules, hint: Hint): BoardHighlight {
  return {
    area: cellsOfUnits(rules, hint.units),
    pattern: hint.step >= 2 ? (hint.pattern ?? []) : [],
    blockers: hint.step >= 2 ? (hint.blockers ?? []) : [],
    target: hint.step >= 3 ? (hint.cell ?? null) : null,
  };
}

/** "Look at row 4.", "Look at rows 2 and 7.", "Look at row 3 and box 5." Rows, columns and boxes count from 1. */
export function whereToLook(units: readonly UnitRef[]): string {
  const order: UnitRef['type'][] = ['row', 'column', 'box'];
  const phrases = order.flatMap((type) => {
    const numbers = units.filter((u) => u.type === type).map((u) => u.index + 1);
    if (numbers.length === 0) return [];
    return [`${type}${numbers.length > 1 ? (type === 'box' ? 'es' : 's') : ''} ${joinAnd(numbers.map(String))}`];
  });
  return phrases.length === 0 ? 'Look at the highlighted cells.' : `Look at ${joinAnd(phrases)}.`;
}

/** What the hint panel shows for a step. */
export interface HintText {
  /** The sentence: where to look, what to look for, or the full explanation. */
  text: string;
  /** The technique's name, shown beside the sentence from step 2. */
  name: string | null;
  /** The longer description behind "What's this?", from step 2. */
  explanation: string | null;
}

export function hintText(hint: Hint): HintText {
  const info = hint.step >= 2 ? strategyInfo(hint.strategy ?? null) : null;
  if (hint.step === 1) return { text: whereToLook(hint.units), name: null, explanation: null };
  if (hint.step === 2) {
    return { text: info?.lookFor || whereToLook(hint.units), name: info?.name ?? null, explanation: info?.explanation || null };
  }
  return { text: hint.text ?? whereToLook(hint.units), name: info?.name ?? null, explanation: info?.explanation || null };
}

function joinAnd(parts: string[]): string {
  if (parts.length <= 1) return parts.join('');
  return `${parts.slice(0, -1).join(', ')} and ${parts.at(-1)}`;
}
