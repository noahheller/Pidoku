import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, fetchDaily, fetchRandom, toPuzzleDef, type ApiPuzzle } from './client';

const apiPuzzle = (overrides: Partial<ApiPuzzle> = {}): ApiPuzzle => ({
  id: 5,
  shareCode: '89TU533C',
  difficulty: 'easy',
  size: 9,
  givens: Array(81).fill(0),
  solution: Array(81).fill(3),
  clueCount: 30,
  score: 120,
  dependency: 4.2,
  hardestStrategy: 'NAKED_PAIR',
  ...overrides,
});

const respond = (status: number, body: unknown) =>
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));

afterEach(() => vi.restoreAllMocks());

describe('toPuzzleDef', () => {
  it('maps an API puzzle to a PuzzleDef keyed by share code', () => {
    expect(toPuzzleDef(apiPuzzle(), 'Easy')).toEqual({
      id: 'api-89TU533C',
      name: 'Easy',
      difficulty: 'Easy',
      size: 9,
      givens: apiPuzzle().givens,
      solution: apiPuzzle().solution,
      score: 120,
      hardestStrategy: 'NAKED_PAIR',
      dependency: 4.2,
    });
  });

  it('rejects grids of the wrong length', () => {
    expect(() => toPuzzleDef(apiPuzzle({ givens: [1, 2, 3] }), 'Easy')).toThrow(ApiError);
  });
});

describe('fetchDaily', () => {
  it('names each daily after its difficulty, keeping the order', async () => {
    respond(200, { date: '2026-11-16', puzzles: [apiPuzzle(), apiPuzzle({ shareCode: 'ABCDEFGH', difficulty: 'hard' })] });
    const daily = await fetchDaily(undefined, new Date(2026, 10, 16, 23, 30));
    expect(daily.date).toBe('2026-11-16');
    expect(daily.puzzles.map((p) => p.name)).toEqual(['Daily Easy', 'Daily Hard']);
    expect(fetch).toHaveBeenCalledWith('/api/puzzles/daily/2026-11-16', expect.anything());
  });

  it('explains a 503 as the puzzles not being ready yet', async () => {
    respond(503, { title: 'Service Unavailable', status: 503, detail: 'No daily puzzles for 2026-11-16' });
    await expect(fetchDaily()).rejects.toMatchObject({ status: 503, message: expect.stringContaining("aren't ready yet") });
  });

  it('reports an unreachable server', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(fetchDaily()).rejects.toMatchObject({ status: null, message: expect.stringContaining("Couldn't reach") });
  });
});

describe('fetchRandom', () => {
  it('asks for the difficulty and names the puzzle after it', async () => {
    respond(200, apiPuzzle({ difficulty: 'fiendish' }));
    const puzzle = await fetchRandom('fiendish');
    expect(puzzle.name).toBe('Fiendish');
    expect(fetch).toHaveBeenCalledWith('/api/puzzles/random?difficulty=fiendish', expect.anything());
  });

  it("uses the problem detail's message for other errors", async () => {
    respond(400, { title: 'Bad Request', status: 400, detail: 'Unknown difficulty: x' });
    await expect(fetchRandom('easy')).rejects.toMatchObject({ status: 400, message: 'Unknown difficulty: x' });
  });

  it.each([502, 504])('treats a %i from the proxy as an unreachable server', async (status) => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('<html>Bad gateway</html>', { status }));
    await expect(fetchRandom('easy')).rejects.toMatchObject({ status, message: expect.stringContaining("Couldn't reach") });
  });

  it("hides a 500's details behind a friendly message", async () => {
    respond(500, { title: 'Internal Server Error', status: 500, detail: 'NullPointerException in PuzzleService' });
    await expect(fetchRandom('easy')).rejects.toMatchObject({ status: 500, message: 'Something went wrong on the Pidoku server. Try again in a moment.' });
  });

  it('falls back to a generic message when a 4xx body is not JSON', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('<html>Not found</html>', { status: 404 }));
    await expect(fetchRandom('easy')).rejects.toMatchObject({ status: 404, message: 'The server returned an error (404).' });
  });
});
