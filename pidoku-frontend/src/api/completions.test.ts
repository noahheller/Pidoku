import { beforeEach, describe, expect, it, vi } from 'vitest';
import { loadPendingCompletions, savePendingCompletions } from '../game/storage';
import { ApiError, completeAttempt, type Attempt } from './client';
import { recordCompletion, sendPendingCompletions } from './completions';

vi.mock('./client', async (original) => ({ ...(await original<typeof import('./client')>()), completeAttempt: vi.fn() }));
const complete = vi.mocked(completeAttempt);

const attempt = (id: number): Attempt => ({ id, puzzleId: 5, startedAt: '2026-11-16T10:00:00Z', completedAt: null, elapsedMs: null, mistakes: 0, hintsUsed: 0, activeMs: null, late: false });
const unreachable = () => new ApiError("Couldn't reach the Pidoku server.", null);
const solve = (grid: number[], mistakes: number) => ({ grid, mistakes, activeMs: 60_000 });
/** A fixed clock: Date.now() as `ms`. */
const at = (ms: number) => () => ms;

beforeEach(() => {
  // Vitest runs these in Node, which has no localStorage.
  const store = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, value),
    removeItem: (key: string) => void store.delete(key),
  });
  complete.mockReset();
});

describe('recordCompletion', () => {
  it('completes the attempt with the active time, not late, and returns the recorded attempt', async () => {
    const recorded = { ...attempt(12), elapsedMs: 61_000 };
    complete.mockResolvedValue(recorded);
    await expect(recordCompletion(Promise.resolve(attempt(12)), solve([3, 1, 4], 2))).resolves.toEqual(recorded);
    expect(complete).toHaveBeenCalledWith(12, { grid: [3, 1, 4], mistakes: 2, activeMs: 60_000, lateByMs: undefined });
    expect(loadPendingCompletions()).toEqual([]);
  });

  it('keeps the solve, with when it was solved, when the API cannot be reached', async () => {
    complete.mockRejectedValue(unreachable());
    await expect(recordCompletion(Promise.resolve(attempt(12)), solve([3, 1, 4], 2), at(1_000))).resolves.toBeNull();
    expect(loadPendingCompletions()).toEqual([{ attemptId: 12, grid: [3, 1, 4], mistakes: 2, activeMs: 60_000, solvedAt: 1_000 }]);
  });

  it('dates the solve from when it happened, not from when the attempt finished starting', async () => {
    complete.mockRejectedValue(unreachable());
    let now = 1_000;
    const slowStart = new Promise<Attempt>((resolve) => setTimeout(() => {
      now = 9_000;
      resolve(attempt(12));
    }, 0));
    await recordCompletion(slowStart, solve([3], 0), () => now);
    expect(loadPendingCompletions()[0].solvedAt).toBe(1_000);
  });

  it.each([500, 503, 401])('keeps the solve for later on a %i', async (status) => {
    complete.mockRejectedValue(new ApiError('nope', status));
    await recordCompletion(Promise.resolve(attempt(12)), solve([3, 1, 4], 0));
    expect(loadPendingCompletions()).toHaveLength(1);
  });

  it.each([404, 422])('gives up on a %i, which will never succeed', async (status) => {
    complete.mockRejectedValue(new ApiError('nope', status));
    await expect(recordCompletion(Promise.resolve(attempt(12)), solve([3, 1, 4], 0))).resolves.toBeNull();
    expect(loadPendingCompletions()).toEqual([]);
  });

  it('does nothing when the attempt never started', async () => {
    await expect(recordCompletion(Promise.resolve(null), solve([3, 1, 4], 0))).resolves.toBeNull();
    expect(complete).not.toHaveBeenCalled();
    expect(loadPendingCompletions()).toEqual([]);
  });
});

describe('sendPendingCompletions', () => {
  const failTwice = async () => {
    complete.mockRejectedValue(unreachable());
    await recordCompletion(Promise.resolve(attempt(12)), solve([3, 1, 4], 2), at(10_000));
    await recordCompletion(Promise.resolve(attempt(13)), solve([1, 5, 9], 0), at(20_000));
    complete.mockReset();
  };

  it('sends the kept solves saying how late they are, and forgets the ones that got through', async () => {
    await failTwice();
    complete.mockResolvedValue(attempt(0));
    await sendPendingCompletions(at(80_000));
    expect(complete).toHaveBeenCalledWith(12, { grid: [3, 1, 4], mistakes: 2, activeMs: 60_000, lateByMs: 70_000 });
    expect(complete).toHaveBeenCalledWith(13, { grid: [1, 5, 9], mistakes: 0, activeMs: 60_000, lateByMs: 60_000 });
    expect(loadPendingCompletions()).toEqual([]);
  });

  it('never says a solve is from the future, if the device clock went back', async () => {
    await failTwice();
    complete.mockResolvedValue(attempt(0));
    await sendPendingCompletions(at(5_000));
    expect(complete).toHaveBeenCalledWith(12, expect.objectContaining({ lateByMs: 0 }));
  });

  it('sends solves saved before their time was kept as before, without a delay', async () => {
    savePendingCompletions([{ attemptId: 7, grid: [3], mistakes: 1 }]);
    complete.mockResolvedValue(attempt(7));
    await sendPendingCompletions(at(80_000));
    expect(complete).toHaveBeenCalledWith(7, { grid: [3], mistakes: 1, activeMs: undefined, lateByMs: undefined });
  });

  it('keeps the ones that still fail', async () => {
    await failTwice();
    complete.mockResolvedValueOnce(attempt(12)).mockRejectedValueOnce(unreachable());
    await sendPendingCompletions(at(80_000));
    expect(loadPendingCompletions()).toEqual([{ attemptId: 13, grid: [1, 5, 9], mistakes: 0, activeMs: 60_000, solvedAt: 20_000 }]);
  });

  it('calls nothing when there is nothing to send', async () => {
    await sendPendingCompletions();
    expect(complete).not.toHaveBeenCalled();
  });
});
