import { describe, expect, it } from 'vitest';
import type { Leaderboard, LeaderboardEntry } from '../api/client';
import { fasterThanPercent, formatGap, gapLine, gaps, ordinal, photoFinishes, standing, timeToBeat } from './leaderboardStats';

const entry = (rank: number, displayName: string | null, seconds: number, you = false): LeaderboardEntry => ({
  rank,
  displayName,
  profileId: null,
  elapsedMs: seconds * 1000,
  penaltyMs: 0,
  mistakes: 0,
  hintsUsed: 0,
  late: false,
  you,
  fastestClean: false,
  supporter: false,
});

const board = (entries: LeaderboardEntry[], overrides: Partial<Leaderboard> = {}): Leaderboard => ({
  date: '2026-11-17',
  difficulty: 'hard',
  friendsOnly: false,
  cleanOnly: false,
  total: entries.length,
  entries,
  you: entries.find((e) => e.you) ?? null,
  fastestClean: null,
  ...overrides,
});

describe('ordinal', () => {
  it.each([
    [1, '1st'],
    [2, '2nd'],
    [3, '3rd'],
    [4, '4th'],
    [11, '11th'],
    [12, '12th'],
    [13, '13th'],
    [21, '21st'],
    [102, '102nd'],
  ])('%i is %s', (n, text) => expect(ordinal(n)).toBe(text));
});

describe('fasterThanPercent', () => {
  it('counts the share of other players who were slower', () => {
    expect(fasterThanPercent(1, 5)).toBe(100);
    expect(fasterThanPercent(2, 5)).toBe(75);
    expect(fasterThanPercent(5, 5)).toBe(0);
  });

  it("doesn't count players tied with you as slower, and never rounds up", () => {
    expect(fasterThanPercent(1, 3, 2)).toBe(50);
    expect(fasterThanPercent(2, 4)).toBe(66);
  });

  it('is null when you are the only one', () => {
    expect(fasterThanPercent(1, 1)).toBeNull();
  });
});

describe('gaps', () => {
  it('measures to the players just above and below', () => {
    expect(gaps([entry(1, 'Ada', 100), entry(2, 'You', 112, true), entry(3, 'Bob', 115)])).toEqual({
      behind: { name: 'Ada', ms: 12000 },
      ahead: { name: 'Bob', ms: 3000 },
    });
  });

  it('is null when you are not on the list', () => {
    expect(gaps([entry(1, 'Ada', 100)])).toBeNull();
  });

  it('measures the ranked times, penalties included', () => {
    const you = { ...entry(2, 'You', 100, true), penaltyMs: 20_000 };
    expect(gaps([entry(1, 'Ada', 110), you, entry(3, 'Bob', 125)])).toEqual({
      behind: { name: 'Ada', ms: 10000 },
      ahead: { name: 'Bob', ms: 5000 },
    });
  });
});

describe('gapLine', () => {
  it('says how far behind and ahead you are', () => {
    expect(gapLine(board([entry(1, 'Ada', 100), entry(2, null, 112, true), entry(3, null, 175)]))).toBe('12s behind Ada · 1:03 ahead of Anonymous');
  });

  it('says when you tied', () => {
    expect(gapLine(board([entry(1, 'Ada', 100), entry(1, 'Me', 100, true)]))).toBe('Tied with Ada');
  });

  it('is null alone on the board', () => {
    expect(gapLine(board([entry(1, 'Me', 100, true)]))).toBeNull();
  });
});

describe('photoFinishes', () => {
  it('marks neighbours less than a second apart', () => {
    const entries = [entry(1, 'A', 100), entry(2, 'B', 100.5), entry(3, 'C', 110)];
    expect([...photoFinishes(entries)]).toEqual([0, 1]);
  });
});

describe('standing', () => {
  it('gives the percentile on the everyone board', () => {
    expect(standing(board([entry(1, 'A', 100), entry(2, 'B', 110, true), entry(3, 'C', 120)]))).toBe('Faster than 50% of solvers.');
  });

  it('celebrates the fastest solve', () => {
    expect(standing(board([entry(1, 'Me', 100, true), entry(2, 'B', 110)]))).toBe('Fastest solve of all 2 players!');
    expect(standing(board([entry(1, 'Me', 100, true), entry(2, 'B', 110)], { cleanOnly: true }))).toBe('Fastest clean solve of all 2 players!');
  });

  it('gives your place among friends', () => {
    expect(standing(board([entry(1, 'A', 100), entry(2, 'Me', 110, true), entry(3, 'C', 120)], { friendsOnly: true }))).toBe("You're 2nd of 3 among you and your friends.");
  });

  it('is null when you have not solved it or are alone', () => {
    expect(standing(board([entry(1, 'A', 100)]))).toBeNull();
    expect(standing(board([entry(1, 'Me', 100, true)]))).toBeNull();
  });
});

describe('formatGap', () => {
  it('uses tenths under a second, seconds under a minute, then m:ss', () => {
    expect(formatGap(40)).toBe('<0.1s');
    expect(formatGap(400)).toBe('0.4s');
    expect(formatGap(12_900)).toBe('12s');
    expect(formatGap(65_000)).toBe('1:05');
  });
});

describe('timeToBeat', () => {
  it("is the fastest friend's solve when a friend has solved it", () => {
    const everyone = board([entry(1, 'Stranger', 300), entry(2, 'Alex', 400)]);
    const friends = board([entry(1, 'Alex', 400)], { friendsOnly: true });
    expect(timeToBeat(everyone, friends)).toEqual({ name: 'Alex', elapsedMs: 400_000, penaltyMs: 0, friend: true });
  });

  it("is the fastest overall when no friend has, or the player is signed out", () => {
    const everyone = board([entry(1, 'Stranger', 300)]);
    expect(timeToBeat(everyone, board([], { friendsOnly: true }))).toMatchObject({ name: 'Stranger', friend: false });
    expect(timeToBeat(everyone, null)).toMatchObject({ name: 'Stranger', friend: false });
  });

  it("skips the player's own solve", () => {
    const friends = board([entry(1, 'Me', 200, true), entry(2, 'Alex', 400)], { friendsOnly: true });
    expect(timeToBeat(board([entry(1, 'Me', 200, true)]), friends)).toMatchObject({ name: 'Alex' });
    expect(timeToBeat(board([entry(1, 'Me', 200, true)]), board([entry(1, 'Me', 200, true)]))).toBeNull();
  });

  it('keeps the penalty, and is null for a puzzle no one has solved', () => {
    expect(timeToBeat(board([{ ...entry(1, null, 300), penaltyMs: 20_000 }]), null)).toEqual({ name: null, elapsedMs: 300_000, penaltyMs: 20_000, friend: false });
    expect(timeToBeat(board([]), null)).toBeNull();
    expect(timeToBeat(null, null)).toBeNull();
  });
});
