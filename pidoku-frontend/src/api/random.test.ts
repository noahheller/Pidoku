import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { PuzzleDef } from '../game/puzzleFile';
import { fetchRandom } from './client';
import { pickRandom } from './random';

vi.mock('./client', async (original) => ({ ...(await original<typeof import('./client')>()), fetchRandom: vi.fn() }));
const fetchMock = vi.mocked(fetchRandom);

const puzzle = (id: string): PuzzleDef => ({ id, name: 'Hard', difficulty: 'Hard', size: 9, givens: [], solution: null, score: null, hardestStrategy: null });

beforeEach(() => fetchMock.mockReset());

describe('pickRandom', () => {
  it('plays a puzzle this browser has not played as it is', async () => {
    fetchMock.mockResolvedValue({ puzzle: puzzle('a'), repeat: false });
    await expect(pickRandom('hard', () => false)).resolves.toEqual({ puzzle: puzzle('a'), replay: false, notice: null });
  });

  it('asks again when this browser already has the puzzle', async () => {
    fetchMock.mockResolvedValueOnce({ puzzle: puzzle('played'), repeat: false }).mockResolvedValueOnce({ puzzle: puzzle('new'), repeat: false });
    const pick = await pickRandom('hard', (p) => p.id === 'played');
    expect(pick.puzzle.id).toBe('new');
    expect(pick.replay).toBe(false);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('replays a repeat from scratch and says the player has played them all', async () => {
    fetchMock.mockResolvedValue({ puzzle: puzzle('old'), repeat: true });
    await expect(pickRandom('hard', () => true)).resolves.toEqual({
      puzzle: puzzle('old'),
      replay: true,
      notice: "You've played every hard puzzle. Here's one again.",
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('settles for a replay after a few puzzles this browser has all played', async () => {
    fetchMock.mockResolvedValue({ puzzle: puzzle('played'), repeat: false });
    const pick = await pickRandom('hard', () => true);
    expect(pick).toMatchObject({ replay: true, notice: expect.stringContaining('played this one before') });
    expect(fetchMock).toHaveBeenCalledTimes(5);
  });
});
