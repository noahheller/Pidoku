import type { PuzzleDef } from '../game/puzzleFile';

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

export interface DailyPuzzles {
  /** The UTC date these are the dailies for, as YYYY-MM-DD. */
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

export async function fetchDaily(signal?: AbortSignal): Promise<DailyPuzzles> {
  const body = await getJson<{ date: string; puzzles: ApiPuzzle[] }>('/api/puzzles/daily', signal, "Today's puzzles aren't ready yet. Try again in a little while.");
  return { date: body.date, puzzles: body.puzzles.map((p) => toPuzzleDef(p, `Daily ${capitalize(p.difficulty)}`)) };
}

export async function fetchRandom(difficulty: Difficulty, signal?: AbortSignal): Promise<PuzzleDef> {
  const body = await getJson<ApiPuzzle>(
    `/api/puzzles/random?difficulty=${encodeURIComponent(difficulty)}`,
    signal,
    `There are no ${difficulty} puzzles ready right now. Try another difficulty or come back later.`,
  );
  return toPuzzleDef(body, capitalize(body.difficulty));
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

/**
 * GETs JSON from the API. Errors become ApiErrors: 503 (the generator job hasn't produced the
 * puzzle yet) gets `unavailableMessage`, other errors the problem detail's `detail` if it has one.
 */
async function getJson<T>(url: string, signal: AbortSignal | undefined, unavailableMessage: string): Promise<T> {
  let response: Response;
  try {
    response = await fetch(url, { headers: { Accept: 'application/json' }, signal });
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
