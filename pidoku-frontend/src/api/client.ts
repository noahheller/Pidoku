import type { PuzzleDef } from '../game/puzzleFile';
import { idToken } from './auth';

/**
 * The Pidoku API. URLs are relative: in production Firebase Hosting rewrites /api/** to Cloud Run
 * (firebase.json), and in development Vite proxies /api to the API on :8080 (vite.config.ts).
 */

export const DIFFICULTIES = ['trivial', 'relaxing', 'easy', 'medium', 'hard', 'fiendish'] as const;
export type Difficulty = (typeof DIFFICULTIES)[number];

/** A puzzle as the API sends it (pidoku-api's PuzzleResponse). */
export interface ApiPuzzle {
  id: number;
  shareCode: string;
  difficulty: Difficulty;
  size: number;
  givens: number[];
  solution: number[];
  clueCount: number;
  score: number;
  dependency: number;
  hardestStrategy: string;
}

/** One go at a puzzle, timed by the server (pidoku-api's AttemptResponse). */
export interface Attempt {
  id: number;
  puzzleId: number;
  /** ISO timestamps; `completedAt` and `elapsedMs` are null until the attempt is completed. */
  startedAt: string;
  completedAt: string | null;
  elapsedMs: number | null;
  mistakes: number;
  hintsUsed: number;
}

export interface DailyPuzzles {
  /** The date these are the dailies for (the player's local date), as YYYY-MM-DD. */
  date: string;
  /** One per daily difficulty, easiest first. */
  puzzles: PuzzleDef[];
}

/** A failed API call, with a message fit to show the player. */
export class ApiError extends Error {
  /** The HTTP status, or null when the API couldn't be reached at all. */
  readonly status: number | null;

  constructor(message: string, status: number | null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * The daily puzzles for the player's own date, so a new day starts at their local midnight.
 * Everyone gets the same puzzles for the same calendar date.
 */
export async function fetchDaily(signal?: AbortSignal, now: Date = new Date()): Promise<DailyPuzzles> {
  // No ID token here: the response is the same for everyone and comes from the CDN's cache.
  const body = await request<{ date: string; puzzles: ApiPuzzle[] }>(`/api/puzzles/daily/${localDate(now)}`, { signal }, "Today's puzzles aren't ready yet. Try again in a little while.");
  return { date: body.date, puzzles: body.puzzles.map((p) => toPuzzleDef(p, `Daily ${capitalize(p.difficulty)}`)) };
}

/** YYYY-MM-DD in the device's own time zone (toISOString would give the UTC date). */
export function localDate(now: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * A random puzzle. Signed in, the API skips puzzles the player has already started; sign-in is
 * optional here, so if the token is refused the puzzle is fetched without it.
 */
export async function fetchRandom(difficulty: Difficulty, signal?: AbortSignal): Promise<PuzzleDef> {
  const url = `/api/puzzles/random?difficulty=${encodeURIComponent(difficulty)}`;
  const unavailable = `There are no ${difficulty} puzzles ready right now. Try another difficulty or come back later.`;
  const token = await idToken();
  let body: ApiPuzzle;
  try {
    body = await request<ApiPuzzle>(url, { signal, token }, unavailable);
  } catch (e) {
    if (!(e instanceof ApiError && e.status === 401)) throw e;
    body = await request<ApiPuzzle>(url, { signal }, unavailable);
  }
  return toPuzzleDef(body, capitalize(body.difficulty));
}

/**
 * Tells the API the player has opened a puzzle, which starts the server's clock on it. Returns
 * the player's unfinished attempt, so opening the puzzle again continues the same one.
 *
 * @param restart true when the player restarted the puzzle, which starts that attempt's clock again
 * @throws ApiError if the player couldn't be signed in or the API refused
 */
export async function startAttempt(puzzleId: number, restart = false): Promise<Attempt> {
  const body = restart ? { puzzleId, restart: true } : { puzzleId };
  return request<Attempt>('/api/attempts', { method: 'POST', body, token: await requireToken() }, BUSY);
}

/** Completes an attempt with the solved grid. The returned `elapsedMs` is the server-measured solve time. */
export async function completeAttempt(attemptId: number, grid: number[], mistakes: number, signal?: AbortSignal): Promise<Attempt> {
  return request<Attempt>(`/api/attempts/${attemptId}/complete`, { method: 'POST', body: { grid, mistakes }, token: await requireToken(), signal }, BUSY);
}

async function requireToken(): Promise<string> {
  const token = await idToken();
  if (token === null) throw new ApiError("Couldn't sign you in, so this game isn't being recorded.", null);
  return token;
}

/** Converts an API puzzle into the game's PuzzleDef. */
export function toPuzzleDef(puzzle: ApiPuzzle, name: string): PuzzleDef {
  const cells = puzzle.size * puzzle.size;
  if (puzzle.givens?.length !== cells || puzzle.solution?.length !== cells) {
    throw new ApiError('The server sent a puzzle in an unexpected format.', null);
  }
  return {
    // Share codes are unique and stable, so saved progress finds the puzzle again.
    id: `api-${puzzle.shareCode}`,
    apiId: puzzle.id,
    name,
    difficulty: capitalize(puzzle.difficulty),
    size: puzzle.size,
    givens: puzzle.givens,
    solution: puzzle.solution,
    score: puzzle.score,
    hardestStrategy: puzzle.hardestStrategy,
    dependency: puzzle.dependency,
  };
}

interface RequestOptions {
  method?: 'GET' | 'POST';
  /** Sent as JSON. */
  body?: unknown;
  /** A Firebase ID token to sign the request with; left out, the request is anonymous. */
  token?: string | null;
  signal?: AbortSignal;
}

/**
 * Calls the API and returns its JSON. Errors become ApiErrors: 503 (the generator job hasn't
 * produced the puzzle yet) gets `unavailableMessage`, other errors the problem detail's `detail`
 * if it has one.
 */
async function request<T>(url: string, { method = 'GET', body, token, signal }: RequestOptions, unavailableMessage: string): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  let response: Response;
  try {
    response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body), signal });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new ApiError(UNREACHABLE, null);
  }
  if (response.ok) {
    try {
      return (await response.json()) as T;
    } catch {
      throw new ApiError('The server sent a response that isn’t valid JSON.', response.status);
    }
  }
  if (response.status === 503) throw new ApiError(unavailableMessage, 503);
  // 502/504 come from whatever sits in front of the API (Firebase Hosting, or Vite's dev proxy)
  // when the API is down or doesn't answer: to the player that's the same as no connection.
  if (response.status === 502 || response.status === 504) throw new ApiError(UNREACHABLE, response.status);
  // Other server errors are bugs; their details mean nothing to a player.
  if (response.status >= 500) throw new ApiError('Something went wrong on the Pidoku server. Try again in a moment.', response.status);
  throw new ApiError((await problemDetail(response)) ?? `The server returned an error (${response.status}).`, response.status);
}

const BUSY = 'The Pidoku server is busy. Try again in a moment.';
const UNREACHABLE = "Couldn't reach the Pidoku server. Check your connection and try again.";

/** The `detail` of an RFC 9457 problem details body, if the response has one. */
async function problemDetail(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json();
    if (typeof body === 'object' && body !== null && typeof (body as { detail?: unknown }).detail === 'string') {
      return (body as { detail: string }).detail;
    }
  } catch {
    // Not JSON (e.g. an HTML error page from a proxy).
  }
  return null;
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
