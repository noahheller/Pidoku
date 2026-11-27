import { describe, expect, it } from 'vitest';
import type { HistoryEntry, OtherSolve } from '../api/client';
import { challengeFrom, historyShownTo, rankedMs, versus, yourSolve } from './history';

const other = (name: string, seconds: number, { you = false, penaltyMs = 0 } = {}): OtherSolve => ({
  playerId: name.length,
  displayName: name,
  you,
  elapsedMs: seconds * 1000,
  penaltyMs,
  mistakes: 0,
  hintsUsed: 0,
  late: false,
});

const entry = (seconds: number, others: OtherSolve[] = [], penaltyMs = 0): HistoryEntry => ({
  puzzle: { id: 'api-ABC', name: 'Hard', difficulty: 'Hard', size: 9, givens: [], solution: null, score: null, hardestStrategy: null, shareCode: 'ABC' },
  solve: { completedAt: '2026-11-20T12:00:00Z', elapsedMs: seconds * 1000, penaltyMs, mistakes: 0, hintsUsed: 0, autoNotesUsed: false, late: false },
  others,
});

const bob = { displayName: 'Bob', you: false, friend: true };

describe('rankedMs', () => {
  it('adds the penalties to the time', () => {
    expect(rankedMs({ elapsedMs: 60_000, penaltyMs: 20_000 })).toBe(80_000);
  });
});

describe('yourSolve', () => {
  it("finds the player's own solve among the others", () => {
    expect(yourSolve(entry(60, [other('Dave', 50), other('Me', 70, { you: true })]))?.displayName).toBe('Me');
    expect(yourSolve(entry(60, [other('Dave', 50)]))).toBeUndefined();
  });
});

describe('challengeFrom', () => {
  it("is the owner's first solve, penalties and all", () => {
    expect(challengeFrom(entry(95, [other('Dave', 50)], 5000), bob)).toEqual({ name: 'Bob', elapsedMs: 95_000, penaltyMs: 5000, friend: true });
  });

  it('says whether the owner is a friend, for when they have no name', () => {
    expect(challengeFrom(entry(95), { displayName: null, you: false, friend: false })).toMatchObject({ name: null, friend: false });
  });

  it("is null on the player's own history", () => {
    expect(challengeFrom(entry(95), { ...bob, you: true })).toBeNull();
  });

  it("is null once the player has solved it: only a first solve ranks", () => {
    expect(challengeFrom(entry(95, [other('Me', 200, { you: true })]), bob)).toBeNull();
  });
});

describe('versus', () => {
  it('compares ranked times, penalties included', () => {
    // 50 s + 20 s of penalties is still faster than 80 s.
    expect(versus(entry(80, [other('Me', 50, { you: true, penaltyMs: 20_000 })]))).toEqual({ outcome: 'faster', byMs: 10_000 });
    expect(versus(entry(80, [other('Me', 90, { you: true })]))).toEqual({ outcome: 'slower', byMs: 10_000 });
    expect(versus(entry(80, [other('Me', 70, { you: true, penaltyMs: 10_000 })]))).toEqual({ outcome: 'tied', byMs: 0 });
  });

  it("is null when the player hasn't solved it", () => {
    expect(versus(entry(80, [other('Dave', 50)]))).toBeNull();
  });
});

describe('historyShownTo', () => {
  it('is never wider than the profile it is on', () => {
    expect(historyShownTo('friends', 'public')).toBe('friends');
    expect(historyShownTo('private', 'public')).toBe('private');
    expect(historyShownTo('public', 'friends')).toBe('friends');
    expect(historyShownTo('public', 'public')).toBe('public');
    expect(historyShownTo('friends', 'private')).toBe('private');
  });
});
