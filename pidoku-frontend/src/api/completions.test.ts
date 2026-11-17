import { beforeEach, describe, expect, it, vi } from 'vitest';
import { loadPendingCompletions } from '../game/storage';
import { ApiError, completeAttempt, type Attempt } from './client';
import { recordCompletion, sendPendingCompletions } from './completions';

vi.mock('./client', async (original) => ({ ...(await original<typeof import('./client')>()), completeAttempt: vi.fn() }));
const complete = vi.mocked(completeAttempt);

const attempt = (id: number): Attempt => ({ id, puzzleId: 5, startedAt: '2026-11-16T10:00:00Z', completedAt: null, elapsedMs: null, mistakes: 0, hintsUsed: 0 });
const unreachable = () => new ApiError("Couldn't reach the Pidoku server.", null);

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
  it('completes the attempt and keeps nothing when the API accepts it', async () => {
    complete.mockResolvedValue(attempt(12));
    await recordCompletion(Promise.resolve(attempt(12)), [3, 1, 4], 2);
    expect(complete).toHaveBeenCalledWith(12, [3, 1, 4], 2);
    expect(loadPendingCompletions()).toEqual([]);
  });

  it('keeps the solve for later when the API cannot be reached', async () => {
    complete.mockRejectedValue(unreachable());
    await recordCompletion(Promise.resolve(attempt(12)), [3, 1, 4], 2);
    expect(loadPendingCompletions()).toEqual([{ attemptId: 12, grid: [3, 1, 4], mistakes: 2 }]);
  });

  it.each([500, 503, 401])('keeps the solve for later on a %i', async (status) => {
    complete.mockRejectedValue(new ApiError('nope', status));
    await recordCompletion(Promise.resolve(attempt(12)), [3, 1, 4], 0);
    expect(loadPendingCompletions()).toHaveLength(1);
  });

  it.each([404, 422])('gives up on a %i, which will never succeed', async (status) => {
    complete.mockRejectedValue(new ApiError('nope', status));
    await recordCompletion(Promise.resolve(attempt(12)), [3, 1, 4], 0);
    expect(loadPendingCompletions()).toEqual([]);
  });

  it('does nothing when the attempt never started', async () => {
    await recordCompletion(Promise.resolve(null), [3, 1, 4], 0);
    expect(complete).not.toHaveBeenCalled();
    expect(loadPendingCompletions()).toEqual([]);
  });
});

describe('sendPendingCompletions', () => {
  const failTwice = async () => {
    complete.mockRejectedValue(unreachable());
    await recordCompletion(Promise.resolve(attempt(12)), [3, 1, 4], 2);
    await recordCompletion(Promise.resolve(attempt(13)), [1, 5, 9], 0);
    complete.mockReset();
  };

  it('sends the kept solves and forgets the ones that got through', async () => {
    await failTwice();
    complete.mockResolvedValue(attempt(0));
    await sendPendingCompletions();
    expect(complete).toHaveBeenCalledWith(12, [3, 1, 4], 2);
    expect(complete).toHaveBeenCalledWith(13, [1, 5, 9], 0);
    expect(loadPendingCompletions()).toEqual([]);
  });

  it('keeps the ones that still fail', async () => {
    await failTwice();
    complete.mockResolvedValueOnce(attempt(12)).mockRejectedValueOnce(unreachable());
    await sendPendingCompletions();
    expect(loadPendingCompletions()).toEqual([{ attemptId: 13, grid: [1, 5, 9], mistakes: 0 }]);
  });

  it('calls nothing when there is nothing to send', async () => {
    await sendPendingCompletions();
    expect(complete).not.toHaveBeenCalled();
  });
});
