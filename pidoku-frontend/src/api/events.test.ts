import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, postEvents } from './client';
import { flushEvents, recordEvent } from './events';

vi.mock('./client', async (original) => ({ ...(await original<typeof import('./client')>()), postEvents: vi.fn() }));
const post = vi.mocked(postEvents);

let store: Map<string, string>;
const queued = () => JSON.parse(store.get('pidoku:pending-events') ?? '[]') as unknown[];
const at = new Date('2026-11-21T10:00:00Z');

beforeEach(() => {
  store = new Map();
  vi.stubGlobal('localStorage', {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, value),
    removeItem: (key: string) => void store.delete(key),
  });
  post.mockReset();
});

describe('events', () => {
  it('sends an event straight away when this browser has an account', async () => {
    post.mockResolvedValue(true);
    recordEvent('share', { puzzleId: 5, detail: { method: 'copy' } }, false, at);
    await flushEvents();
    expect(post).toHaveBeenCalledWith([{ type: 'share', puzzleId: 5, detail: { method: 'copy' }, occurredAt: '2026-11-21T10:00:00.000Z' }], false);
    expect(queued()).toEqual([]);
  });

  it('keeps events until there is an account to send them for', async () => {
    post.mockResolvedValue(false);
    recordEvent('share_arrival', { detail: { newVisitor: true } }, false, at);
    await flushEvents();
    expect(queued()).toHaveLength(1);

    post.mockResolvedValue(true);
    await flushEvents();
    expect(post).toHaveBeenLastCalledWith([expect.objectContaining({ type: 'share_arrival' })], false);
    expect(queued()).toEqual([]);
  });

  it('keeps events the server could not take now, and drops ones it refuses for good', async () => {
    post.mockRejectedValue(new ApiError('down', 503));
    recordEvent('abandoned', {}, false, at);
    await flushEvents();
    expect(queued()).toHaveLength(1);

    post.mockRejectedValue(new ApiError('bad', 400));
    await flushEvents();
    expect(queued()).toEqual([]);
  });

  it('holds at most 50 events, dropping the oldest', async () => {
    post.mockResolvedValue(false);
    for (let i = 0; i < 55; i++) recordEvent('abandoned', { detail: { i } }, false, at);
    await flushEvents();
    const events = queued() as { detail: { i: number } }[];
    expect(events).toHaveLength(50);
    expect(events[0].detail.i).toBe(5);
  });
});
