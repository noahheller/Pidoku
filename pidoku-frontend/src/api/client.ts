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
  /** The date (YYYY-MM-DD) the puzzle is the daily for, or null if it isn't a daily. */
  dailyDate: string | null;
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
  /** The client's own timer as sent on completion; null if it wasn't sent. */
  activeMs: number | null;
  /** The completion was sent some time after the solve, and the server took that delay off. */
  late: boolean;
}

/** The signed-in player (pidoku-api's PlayerResponse). */
export interface Player {
  id: number;
  /** Null until the player picks one. */
  displayName: string | null;
  /** What friends enter (or open as /f/{code}) to add this player. */
  friendCode: string;
  createdAt: string;
}

/** One of the player's friends (pidoku-api's Friend). */
export interface Friend {
  /** Their player id, for removing them. */
  id: number;
  displayName: string | null;
}

/** The difficulties each day has a daily of, easiest first (`pidoku.daily.difficulties` in the API). */
export const DAILY_DIFFICULTIES = ['easy', 'medium', 'hard'] as const satisfies readonly Difficulty[];

/** One player's ranked solve of a daily (pidoku-api's Leaderboard.Entry). */
export interface LeaderboardEntry {
  /** 1 for the fastest; equal times share a rank. */
  rank: number;
  displayName: string | null;
  /** The server-measured solve time. */
  elapsedMs: number;
  mistakes: number;
  /** Sent late, so the time is partly what the player's device claimed. */
  late: boolean;
  /** The signed-in player's own solve. */
  you: boolean;
}

/** The fastest solves of one daily (pidoku-api's Leaderboard). */
export interface Leaderboard {
  date: string;
  difficulty: Difficulty;
  friendsOnly: boolean;
  /** Everyone ranked, including those beyond `entries`. */
  total: number;
  /** The fastest, best first (at most 50). */
  entries: LeaderboardEntry[];
  /** The player's own entry, also when it's beyond `entries`; null if they haven't solved it. */
  you: LeaderboardEntry | null;
}

/** The player's run of consecutive dates with a solved daily (pidoku-api's Streak). */
export interface Streak {
  /** Ends today, or yesterday if today has no solve yet; 0 if neither has one. */
  current: number;
  best: number;
  solvedToday: boolean;
}

/** What the game reports when a puzzle is solved. */
export interface Completion {
  /** The finished grid, flat and row-major. */
  grid: number[];
  mistakes: number;
  /** The game's own timer, which pauses while the tab is hidden. */
  activeMs?: number;
  /** How long ago the puzzle was solved, for a completion that couldn't be sent at the time. */
  lateByMs?: number;
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
  // dailyDate comes with each puzzle, but the response's date is the same thing.
  return { date: body.date, puzzles: body.puzzles.map((p) => toPuzzleDef({ ...p, dailyDate: p.dailyDate ?? body.date })) };
}

/** The puzzle behind a share link (/p/{shareCode}). */
export async function fetchByShareCode(shareCode: string, signal?: AbortSignal): Promise<PuzzleDef> {
  const body = await request<ApiPuzzle>(`/api/puzzles/${encodeURIComponent(shareCode)}`, { signal }, BUSY);
  return toPuzzleDef(body);
}

/** YYYY-MM-DD in the device's own time zone (toISOString would give the UTC date). */
export function localDate(now: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** A puzzle from the random endpoint. */
export interface RandomPuzzle {
  puzzle: PuzzleDef;
  /** The player has started every puzzle of this difficulty, so this is one they've played before. */
  repeat: boolean;
}

/**
 * A random puzzle. Signed in, the API skips puzzles the player has already started, and once
 * they've started them all says the one it sends is a `repeat`. Sign-in is optional here, so if
 * the token is refused the puzzle is fetched without it.
 */
export async function fetchRandom(difficulty: Difficulty, signal?: AbortSignal): Promise<RandomPuzzle> {
  const url = `/api/puzzles/random?difficulty=${encodeURIComponent(difficulty)}`;
  const unavailable = `There are no ${difficulty} puzzles ready right now. Try another difficulty or come back later.`;
  const token = await idToken();
  let body: ApiPuzzle & { repeat?: boolean };
  try {
    body = await request<typeof body>(url, { signal, token }, unavailable);
  } catch (e) {
    if (!(e instanceof ApiError && e.status === 401)) throw e;
    body = await request<typeof body>(url, { signal }, unavailable);
  }
  return { puzzle: toPuzzleDef(body), repeat: body.repeat === true };
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
export async function completeAttempt(attemptId: number, completion: Completion, signal?: AbortSignal): Promise<Attempt> {
  return request<Attempt>(`/api/attempts/${attemptId}/complete`, { method: 'POST', body: completion, token: await requireToken(), signal }, BUSY);
}

/** The signed-in player, signing them in (and creating the player) if needed. */
export async function fetchPlayer(signal?: AbortSignal): Promise<Player> {
  return request<Player>('/api/players/me', { token: await requireToken(), signal }, BUSY);
}

/**
 * Sets the name friends see. The server trims it and allows 1–40 characters.
 *
 * @throws ApiError with the server's reason (fit to show) if the name isn't allowed
 */
export async function updateDisplayName(displayName: string): Promise<Player> {
  return request<Player>('/api/players/me', { method: 'PATCH', body: { displayName }, token: await requireToken() }, BUSY);
}

/** The player's current and best streak, as of their own local date. */
export async function fetchStreak(signal?: AbortSignal, now: Date = new Date()): Promise<Streak> {
  return request<Streak>(`/api/players/me/streak?today=${localDate(now)}`, { token: await requireToken(), signal }, BUSY);
}

/** The player's friends. */
export async function fetchFriends(signal?: AbortSignal): Promise<Friend[]> {
  return request<Friend[]>('/api/friends', { token: await requireToken(), signal }, BUSY);
}

/**
 * Adds the player with this friend code as a friend, both ways.
 *
 * @throws ApiError with the server's reason (fit to show) for an unknown code or the player's own
 */
export async function addFriend(friendCode: string): Promise<Friend> {
  return request<Friend>('/api/friends', { method: 'POST', body: { friendCode }, token: await requireToken() }, BUSY);
}

/** Removes a friend, for both players. */
export async function removeFriend(friendId: number): Promise<void> {
  await request<void>(`/api/friends/${friendId}`, { method: 'DELETE', token: await requireToken() }, BUSY);
}

/**
 * The fastest solves of one daily. Sign-in is optional for everyone's times (signed in, the
 * player's own entry is marked), and needed for friends only.
 */
export async function fetchLeaderboard(date: string, difficulty: Difficulty, friendsOnly: boolean, signal?: AbortSignal): Promise<Leaderboard> {
  const url = `/api/puzzles/daily/${encodeURIComponent(date)}/leaderboard?difficulty=${encodeURIComponent(difficulty)}${friendsOnly ? '&friends=true' : ''}`;
  if (friendsOnly) return request<Leaderboard>(url, { token: await requireToken(), signal }, BUSY);
  const token = await idToken();
  try {
    return await request<Leaderboard>(url, { token, signal }, BUSY);
  } catch (e) {
    // Like fetchRandom: a refused token still gets everyone's times.
    if (!(token && e instanceof ApiError && e.status === 401)) throw e;
    return request<Leaderboard>(url, { signal }, BUSY);
  }
}

async function requireToken(): Promise<string> {
  const token = await idToken();
  if (token === null) throw new ApiError("Couldn't sign you in, so this game isn't being recorded.", null);
  return token;
}

/** Converts an API puzzle into the game's PuzzleDef, named e.g. "Daily Hard" or "Hard". */
export function toPuzzleDef(puzzle: ApiPuzzle, name = `${puzzle.dailyDate ? 'Daily ' : ''}${capitalize(puzzle.difficulty)}`): PuzzleDef {
  const cells = puzzle.size * puzzle.size;
  if (puzzle.givens?.length !== cells || puzzle.solution?.length !== cells) {
    throw new ApiError('The server sent a puzzle in an unexpected format.', null);
  }
  return {
    // Share codes are unique and stable, so saved progress finds the puzzle again.
    id: `api-${puzzle.shareCode}`,
    apiId: puzzle.id,
    shareCode: puzzle.shareCode,
    dailyDate: puzzle.dailyDate ?? undefined,
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
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
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
    if (response.status === 204) return undefined as T;
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
