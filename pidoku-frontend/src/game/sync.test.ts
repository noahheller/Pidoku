import { describe, expect, it } from 'vitest';
import type { Attempt } from '../api/client';
import type { SavedProgress } from './game';
import { boardChanged, newerOnServer } from './sync';

const board = (overrides: Partial<SavedProgress> = {}): SavedProgress => ({
  values: [3, 0, 4],
  notes: [0, 2, 0],
  mistakes: 0,
  elapsedMs: 30_000,
  completed: false,
  savedAt: Date.parse('2026-11-21T10:00:00Z'),
  ...overrides,
});

const attempt = (overrides: Partial<Attempt> = {}): Attempt => ({
  id: 12,
  puzzleId: 5,
  startedAt: '2026-11-21T09:00:00Z',
  completedAt: null,
  elapsedMs: null,
  mistakes: 0,
  hintsUsed: 0,
  hintPenaltyMs: 0,
  penaltyMs: 0,
  activeMs: null,
  late: false,
  progress: { values: [3, 1, 4], notes: [0, 0, 0], mistakes: 1, activeMs: 90_000 },
  progressUpdatedAt: '2026-11-21T11:00:00Z',
  autoNotesUsed: true,
  ...overrides,
});

describe('newerOnServer', () => {
  it("takes the server's board when it was saved after this browser's", () => {
    expect(newerOnServer(board(), attempt())).toEqual({
      values: [3, 1, 4],
      notes: [0, 0, 0],
      mistakes: 1,
      elapsedMs: 90_000,
      completed: false,
      autoNotesUsed: true,
      savedAt: Date.parse('2026-11-21T11:00:00Z'),
    });
  });

  it("takes the server's board when this browser has none", () => {
    expect(newerOnServer(null, attempt())).toMatchObject({ values: [3, 1, 4] });
  });

  it("keeps this browser's board when the server's is the same board, uploaded after it last changed", () => {
    const local = board({ values: [3, 1, 4], notes: [0, 0, 0], mistakes: 1, autoNotesUsed: true, elapsedMs: 95_000 });
    expect(newerOnServer(local, attempt())).toBeNull();
  });

  it("keeps this browser's board when it's newer", () => {
    expect(newerOnServer(board({ savedAt: Date.parse('2026-11-21T12:00:00Z') }), attempt())).toBeNull();
  });

  it("keeps this browser's board when the server has none, or the attempt is finished", () => {
    expect(newerOnServer(board(), attempt({ progress: null, progressUpdatedAt: null }))).toBeNull();
    expect(newerOnServer(board(), attempt({ completedAt: '2026-11-21T11:30:00Z' }))).toBeNull();
    expect(newerOnServer(board({ completed: true }), attempt())).toBeNull();
  });

  it('compares the timer for a save from before saves were timestamped', () => {
    expect(newerOnServer(board({ savedAt: undefined, elapsedMs: 120_000 }), attempt())).toBeNull();
    expect(newerOnServer(board({ savedAt: undefined, elapsedMs: 60_000 }), attempt())).not.toBeNull();
  });
});

describe('boardChanged', () => {
  it('ignores the timer and notices digits, notes, mistakes and auto notes', () => {
    expect(boardChanged(board(), board({ elapsedMs: 99_000 }))).toBe(false);
    expect(boardChanged(board(), board({ values: [3, 1, 4] }))).toBe(true);
    expect(boardChanged(board(), board({ notes: [0, 0, 0] }))).toBe(true);
    expect(boardChanged(board(), board({ mistakes: 1 }))).toBe(true);
    expect(boardChanged(board(), board({ autoNotesUsed: true }))).toBe(true);
    expect(boardChanged(null, board())).toBe(true);
  });
});
