/**
 * Player-facing names and descriptions for the engine's solving techniques (pidoku-engine's
 * StrategyEnum), as the puzzles' `hardestStrategy` sends them.
 */

/** What a player is told about a technique. */
export interface StrategyInfo {
  /** The technique's name, shown as a label: "Hidden pair". */
  name: string;
  /**
   * What to look for, in plain words, for someone who has never heard the name. One sentence,
   * and it never names the technique, so it works as a hint on its own with the name beside it.
   */
  lookFor: string;
  /** A few sentences on why it works, for a "What's this?" card behind the name. */
  explanation: string;
}

const TECHNIQUES: Record<string, StrategyInfo> = {
  HIDDEN_SINGLE_BOX: {
    name: 'Hidden single (box)',
    lookFor: 'In this box, a digit has only one cell left where it can go (or, for a 1 or 5 the box still needs both of, only two cells).',
    explanation:
      'Pick a digit the box still needs and check its empty cells: a copy of that digit already in a cell\'s row or column rules the cell out, once that row or column has all the copies it allows. ' +
      'When the cells left match the copies still needed (one cell for most digits, two for a 1 or 5 the box is missing both of), those cells must hold that digit.',
  },
  HIDDEN_SINGLE_LINE: {
    name: 'Hidden single (row or column)',
    lookFor: 'In this row or column, a digit has only one cell left where it can go (or, for a 1 or 5 it still needs both of, only two cells).',
    explanation:
      'Pick a digit the row or column still needs and check its empty cells: the digit is ruled out of a cell whose box, or crossing row or column, already holds all the copies it allows. ' +
      'When the cells left match the copies still needed (one for most digits, two for a 1 or 5 that\'s missing both), those cells must hold that digit.',
  },
  NAKED_SINGLE: {
    name: 'Naked single',
    lookFor: 'One cell has only one digit that can go in it.',
    explanation:
      'Look at everything in the cell\'s row, column and box. A digit is ruled out once any of them already holds all the copies π allows: one of 2, 3, 4, 6 and 9, two of 1 and 5. ' +
      'When only one digit is left for the cell, that\'s its digit.',
  },
  NAKED_PAIR: {
    name: 'Naked pair',
    lookFor: 'Two cells here can only hold the same two digits, so those digits can\'t go anywhere else in this row, column or box.',
    explanation:
      'If two cells in a row, column or box can only take digits from the same pair, and the unit still needs exactly two more of those digits, the two cells must take them. ' +
      'Every other cell in the unit loses those digits as candidates. Nothing is placed yet, but the notes get simpler.',
  },
  INTERSECTION_REMOVAL: {
    name: 'Pointing and claiming',
    lookFor: 'Every place left for a digit in this box lies in one row or column (or the reverse: every place in a row or column lies in one box), so the digit comes out of the rest of the other one.',
    explanation:
      'Pointing: if all of a box\'s remaining spots for a digit sit in one row, and that row needs no more copies than the box does, the box\'s copies cover the row, so the digit can\'t go in the row outside the box. ' +
      'Claiming is the same the other way round: a row or column whose spots for a digit all sit in one box takes the box\'s copies, so the digit leaves the rest of the box.',
  },
  HIDDEN_PAIR: {
    name: 'Hidden pair',
    lookFor: 'Two digits here can only go in the same few cells, so those cells can\'t hold anything else.',
    explanation:
      'If the only cells in a row, column or box that can take two of its missing digits are exactly as many as the copies still needed, those cells must hold them. ' +
      'Usually that\'s two cells; with a 1 or 5 the unit still needs twice, it\'s three. Every other candidate in those cells can go.',
  },
  NAKED_TRIPLE: {
    name: 'Naked triple',
    lookFor: 'Three cells here can only hold digits from the same three, so those digits can\'t go anywhere else in this row, column or box.',
    explanation:
      'Like a pair, but with three cells. Not every cell needs all three digits (one might only be able to hold two of them), as long as the three cells together can only take those digits and the unit needs exactly three more of them. ' +
      'Those digits leave every other cell in the unit.',
  },
  HIDDEN_TRIPLE: {
    name: 'Hidden triple',
    lookFor: 'Three digits here can only go in the same few cells, so those cells can\'t hold anything else.',
    explanation:
      'Find three missing digits whose only possible cells in the unit are exactly as many as the copies still needed. Those cells must hold those digits, so every other candidate in them can go. ' +
      'The digits are hidden among the cells\' other notes, which is what makes it hard to see.',
  },
  NAKED_QUAD: {
    name: 'Naked quad',
    lookFor: 'Four cells here can only hold digits from the same four, so those digits can\'t go anywhere else in this row, column or box.',
    explanation:
      'The same idea as a pair or triple, with four cells. The four cells together can only take those digits and the unit needs exactly four more of them, so every other cell in the unit loses them.',
  },
  X_WING: {
    name: 'X-Wing',
    lookFor: 'In two rows, a digit can only go in the same two columns (or in two columns, only the same two rows), so it can\'t go anywhere else in those columns.',
    explanation:
      'Take a digit that appears once per unit (not 1 or 5). If two rows each have only two cells left for it, in the same two columns, then each row puts it in one of those columns: one in each, on opposite corners of a rectangle. ' +
      'Either way both columns are filled, so the digit comes out of every other cell in those two columns. It works the same with rows and columns swapped.',
  },
  HIDDEN_QUAD: {
    name: 'Hidden quad',
    lookFor: 'Four digits here can only go in the same few cells, so those cells can\'t hold anything else.',
    explanation:
      'Find four missing digits whose only possible cells in the unit are exactly as many as the copies still needed. Those cells must hold those digits, so every other candidate in them can go.',
  },
  W_WING: {
    name: 'W-Wing',
    lookFor: 'Two cells that can only be the same two digits are linked by a unit where one of those digits has only two places left, so the other digit can\'t go in any cell that sees both.',
    explanation:
      'Two cells far apart can each only be X or Y. Somewhere else, a row, column or box has only two places left for X, and each of those places sees one of the two cells. ' +
      'One of those places must be X, so at least one of the two cells isn\'t X, which makes it Y. Any cell that sees both of them can\'t be Y. Only for digits that appear once per unit.',
  },
};

/**
 * Everything a player is told about a technique, or null for no technique. A technique the engine
 * adds later gets a name from its constant ("Some new trick" from SOME_NEW_TRICK) and no
 * descriptions until it's added here.
 */
export function strategyInfo(strategy: string | null | undefined): StrategyInfo | null {
  if (!strategy) return null;
  const known = TECHNIQUES[strategy];
  if (known) return known;
  const words = strategy.toLowerCase().split('_').filter(Boolean).join(' ');
  return { name: words.charAt(0).toUpperCase() + words.slice(1), lookFor: '', explanation: '' };
}

/** "Hidden pair" for HIDDEN_PAIR, or null for no technique. */
export function strategyName(strategy: string | null | undefined): string | null {
  return strategyInfo(strategy)?.name ?? null;
}

/** The engine constants this file describes, for tests. */
export const DESCRIBED_STRATEGIES = Object.keys(TECHNIQUES);
