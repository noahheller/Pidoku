import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { idToken } from './auth';
import { addFriend, ApiError, fetchFriendProfile, completeAttempt, fetchDaily, fetchLeaderboard, fetchRandom, removeFriend, startAttempt, toPuzzleDef, type ApiPuzzle, type Attempt, type Leaderboard } from './client';

// Signed out unless a test signs in: no test talks to Firebase.
vi.mock('./auth', () => ({ idToken: vi.fn() }));
const signIn = (token: string | null) => vi.mocked(idToken).mockResolvedValue(token);
beforeEach(() => signIn(null));

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
  dailyDate: null,
  ...overrides,
});

const respond = (status: number, body: unknown) =>
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));

afterEach(() => vi.restoreAllMocks());

describe('toPuzzleDef', () => {
  it('maps an API puzzle to a PuzzleDef keyed by share code', () => {
    expect(toPuzzleDef(apiPuzzle(), 'Easy')).toEqual({
      id: 'api-89TU533C',
      apiId: 5,
      shareCode: '89TU533C',
      dailyDate: undefined,
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

  it('names a puzzle after its difficulty, and a daily as one, keeping its date', () => {
    expect(toPuzzleDef(apiPuzzle({ difficulty: 'hard' })).name).toBe('Hard');
    expect(toPuzzleDef(apiPuzzle({ difficulty: 'hard', dailyDate: '2026-11-17' }))).toMatchObject({ name: 'Daily Hard', dailyDate: '2026-11-17' });
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

  it('never sends the ID token, so the CDN can cache the response', async () => {
    signIn('token-1');
    const fetchMock = respond(200, { date: '2026-11-16', puzzles: [apiPuzzle()] });
    await fetchDaily();
    expect(fetchMock.mock.calls[0][1]?.headers).not.toHaveProperty('Authorization');
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
    const { puzzle, repeat } = await fetchRandom('fiendish');
    expect(puzzle.name).toBe('Fiendish');
    expect(repeat).toBe(false);
    expect(fetch).toHaveBeenCalledWith('/api/puzzles/random?difficulty=fiendish', expect.anything());
  });

  it('says when the puzzle is a repeat', async () => {
    respond(200, { ...apiPuzzle(), repeat: true });
    await expect(fetchRandom('easy')).resolves.toMatchObject({ repeat: true });
  });

  it('sends the ID token when signed in', async () => {
    signIn('token-1');
    const fetchMock = respond(200, apiPuzzle());
    await fetchRandom('easy');
    expect(fetchMock.mock.calls[0][1]?.headers).toMatchObject({ Authorization: 'Bearer token-1' });
  });

  it('sends no Authorization header when signed out', async () => {
    const fetchMock = respond(200, apiPuzzle());
    await fetchRandom('easy');
    expect(fetchMock.mock.calls[0][1]?.headers).not.toHaveProperty('Authorization');
  });

  it('fetches without the token if the API refuses it', async () => {
    signIn('expired');
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(apiPuzzle()), { status: 200 }));
    await expect(fetchRandom('easy')).resolves.toMatchObject({ puzzle: { id: 'api-89TU533C' } });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][1]?.headers).not.toHaveProperty('Authorization');
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

describe('attempts', () => {
  const attempt: Attempt = { id: 12, puzzleId: 5, startedAt: '2026-11-16T10:00:00Z', completedAt: null, elapsedMs: null, mistakes: 0, hintsUsed: 0, activeMs: null, late: false };

  it('starts an attempt at a puzzle as the signed-in player', async () => {
    signIn('token-1');
    const fetchMock = respond(200, attempt);
    await expect(startAttempt(5)).resolves.toEqual(attempt);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/attempts',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ puzzleId: 5 }), headers: expect.objectContaining({ Authorization: 'Bearer token-1', 'Content-Type': 'application/json' }) }),
    );
  });

  it('says so when the player restarted the puzzle', async () => {
    signIn('token-1');
    const fetchMock = respond(200, attempt);
    await startAttempt(5, true);
    expect(fetchMock).toHaveBeenCalledWith('/api/attempts', expect.objectContaining({ body: JSON.stringify({ puzzleId: 5, restart: true }) }));
  });

  it('completes an attempt with the grid and mistakes', async () => {
    signIn('token-1');
    const fetchMock = respond(200, { ...attempt, completedAt: '2026-11-16T10:04:12Z', elapsedMs: 252000, mistakes: 2 });
    await expect(completeAttempt(12, { grid: [3, 1, 4], mistakes: 2 })).resolves.toMatchObject({ elapsedMs: 252000 });
    expect(fetchMock).toHaveBeenCalledWith('/api/attempts/12/complete', expect.objectContaining({ method: 'POST', body: JSON.stringify({ grid: [3, 1, 4], mistakes: 2 }) }));
  });

  it('sends the active time and how late the completion is when given', async () => {
    signIn('token-1');
    const fetchMock = respond(200, { ...attempt, completedAt: '2026-11-16T10:04:12Z', elapsedMs: 252000 });
    await completeAttempt(12, { grid: [3], mistakes: 0, activeMs: 250000, lateByMs: 60000 });
    expect(fetchMock).toHaveBeenCalledWith('/api/attempts/12/complete', expect.objectContaining({ body: JSON.stringify({ grid: [3], mistakes: 0, activeMs: 250000, lateByMs: 60000 }) }));
  });

  it('fails without calling the API when the player is signed out', async () => {
    const fetchMock = respond(200, attempt);
    await expect(startAttempt(5)).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('fetchLeaderboard', () => {
  const board: Leaderboard = { date: '2026-11-17', difficulty: 'hard', friendsOnly: false, cleanOnly: false, total: 0, entries: [], you: null, firstToFinish: null, fastestClean: null };

  it("asks for a daily's leaderboard, signed in if possible", async () => {
    signIn('token-1');
    const fetchMock = respond(200, board);
    await expect(fetchLeaderboard('2026-11-17', 'hard', { friendsOnly: false, cleanOnly: false })).resolves.toEqual(board);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/puzzles/daily/2026-11-17/leaderboard?difficulty=hard');
    expect(fetchMock.mock.calls[0][1]?.headers).toMatchObject({ Authorization: 'Bearer token-1' });
  });

  it("shows everyone's times signed out", async () => {
    const fetchMock = respond(200, board);
    await fetchLeaderboard('2026-11-17', 'hard', { friendsOnly: false, cleanOnly: false });
    expect(fetchMock.mock.calls[0][1]?.headers).not.toHaveProperty('Authorization');
  });

  it("fetches everyone's times without the token if the API refuses it", async () => {
    signIn('expired');
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response('', { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify(board), { status: 200 }));
    await expect(fetchLeaderboard('2026-11-17', 'hard', { friendsOnly: false, cleanOnly: false })).resolves.toEqual(board);
    expect(fetchMock.mock.calls[1][1]?.headers).not.toHaveProperty('Authorization');
  });

  it('asks for clean solves only', async () => {
    const fetchMock = respond(200, { ...board, cleanOnly: true });
    await fetchLeaderboard('2026-11-17', 'hard', { friendsOnly: false, cleanOnly: true });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/puzzles/daily/2026-11-17/leaderboard?difficulty=hard&clean=true');
  });

  it('asks for friends only, which needs sign-in', async () => {
    signIn('token-1');
    const fetchMock = respond(200, { ...board, friendsOnly: true });
    await fetchLeaderboard('2026-11-17', 'easy', { friendsOnly: true, cleanOnly: false });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/puzzles/daily/2026-11-17/leaderboard?difficulty=easy&friends=true');

    signIn(null);
    await expect(fetchLeaderboard('2026-11-17', 'easy', { friendsOnly: true, cleanOnly: false })).rejects.toBeInstanceOf(ApiError);
  });
});

describe('friends', () => {
  it('adds a friend by code', async () => {
    signIn('token-1');
    const fetchMock = respond(200, { id: 3, displayName: 'Bob' });
    await expect(addFriend('7kq4m2xd')).resolves.toEqual({ id: 3, displayName: 'Bob' });
    expect(fetchMock).toHaveBeenCalledWith('/api/friends', expect.objectContaining({ method: 'POST', body: JSON.stringify({ friendCode: '7kq4m2xd' }) }));
  });

  it("passes on the server's reason for refusing a code", async () => {
    signIn('token-1');
    respond(404, { status: 404, detail: "No player has the friend code 'ZZZZZZZZ'." });
    await expect(addFriend('ZZZZZZZZ')).rejects.toMatchObject({ status: 404, message: "No player has the friend code 'ZZZZZZZZ'." });
  });

  it("fetches a friend's profile as of the player's date", async () => {
    signIn('token-1');
    const fetchMock = respond(200, { id: 3, displayName: 'Bob' });
    await fetchFriendProfile(3, undefined, new Date(2026, 10, 17, 23, 30));
    expect(fetchMock.mock.calls[0][0]).toBe('/api/friends/3?today=2026-11-17');
  });

  it('removes a friend, expecting no content back', async () => {
    signIn('token-1');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));
    await expect(removeFriend(3)).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith('/api/friends/3', expect.objectContaining({ method: 'DELETE' }));
  });
});
