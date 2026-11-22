import { expect, type APIRequestContext, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

/**
 * Helpers for the e2e tests. Each test's players are new: a fresh uid, signed in with the API's
 * test token `e2e.<uid>` (see the e2e build in src/api/auth.ts and the API's E2eConfiguration).
 * Dailies are shared by every test in a run, so tests never assume they're the only solvers.
 */

export interface ApiPuzzle {
  id: number;
  shareCode: string;
  difficulty: string;
  givens: number[];
  solution: number[];
}

export interface Player {
  uid: string;
  token: string;
  context: BrowserContext;
  page: Page;
}

/** Today in the browser's zone (UTC, see playwright.config.ts). */
export function today(offsetDays = 0): string {
  return new Date(Date.now() + offsetDays * 86_400_000).toISOString().slice(0, 10);
}

/**
 * A new player in their own browser context. `uid` makes two contexts the same player on two
 * devices. Unless `tutorial`, they've already seen the tutorial, so a puzzle opens straight away.
 */
export async function newPlayer(browser: Browser, { uid = `e2e-${randomUUID()}`, tutorial = false } = {}): Promise<Player> {
  const context = await browser.newContext({ permissions: ['clipboard-read', 'clipboard-write'] });
  await context.addInitScript(
    ({ uid, tutorial }) => {
      try {
        localStorage.setItem('pidoku:e2e-uid', uid);
        if (!tutorial) localStorage.setItem('pidoku:tutorial-seen', 'true');
      } catch {
        // about:blank has no storage.
      }
    },
    { uid, tutorial },
  );
  return { uid, token: `e2e.${uid}`, context, page: await context.newPage() };
}

/** Every analytics event a page sends (POST /api/events), as it sends them. */
export function sentEvents(page: Page): { type: string; detail?: Record<string, unknown> }[] {
  const events: { type: string; detail?: Record<string, unknown> }[] = [];
  page.on('request', (request) => {
    if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/events') events.push(...(request.postDataJSON() as typeof events));
  });
  return events;
}

/** Today's dailies, by difficulty. */
export async function dailies(request: APIRequestContext): Promise<Record<string, ApiPuzzle>> {
  const response = await request.get(`/api/puzzles/daily/${today()}`);
  expect(response.ok()).toBe(true);
  const { puzzles } = (await response.json()) as { puzzles: ApiPuzzle[] };
  expect(puzzles.map((p) => p.difficulty)).toEqual(['easy', 'medium', 'hard']);
  return Object.fromEntries(puzzles.map((p) => [p.difficulty, p]));
}

/** Opens one of today's dailies from the home screen and presses Start. */
export async function openDaily(page: Page, name: string): Promise<void> {
  await page.goto('/');
  await page.getByRole('region', { name: 'Today' }).getByRole('button', { name: new RegExp(`^${name}`) }).click();
  await expect(page.getByRole('heading', { level: 1, name: new RegExp(`^${name}`) })).toBeVisible();
  await page.getByRole('button', { name: 'Start', exact: true }).click();
  await expect(page.getByRole('grid', { name: 'Pidoku board' })).toBeVisible();
}

export function cell(page: Page, index: number) {
  return page.getByRole('gridcell').nth(index);
}

/** Taps a cell, then a digit on the number pad (as on a phone). */
export async function enter(page: Page, index: number, digit: number): Promise<void> {
  await cell(page, index).click();
  await page.getByRole('button', { name: new RegExp(`^Enter ${digit},`) }).click();
}

/** Fills every empty cell from the solution, or the first `count` of them. */
export async function fill(page: Page, puzzle: Pick<ApiPuzzle, 'givens' | 'solution'>, count = Infinity): Promise<number[]> {
  const empties = puzzle.givens.flatMap((v, i) => (v === 0 ? [i] : [])).slice(0, count);
  for (const i of empties) await enter(page, i, puzzle.solution[i]);
  return empties;
}

/** The game's timer, in seconds. */
export async function timerSeconds(page: Page): Promise<number> {
  const text = (await page.getByTitle('Time', { exact: true }).textContent())?.trim() ?? '';
  const parts = text.split(':').map(Number);
  expect(parts.every(Number.isFinite), `timer "${text}"`).toBe(true);
  return parts.reduce((total, part) => total * 60 + part, 0);
}

/** Calls the API as a player, for setup that isn't what the test is about. */
export async function api<T>(request: APIRequestContext, player: Pick<Player, 'token'>, method: 'GET' | 'POST' | 'PATCH', url: string, data?: unknown): Promise<T> {
  const response = await request.fetch(url, { method, data, headers: { Authorization: `Bearer ${player.token}` } });
  expect(response.ok(), `${method} ${url}: ${response.status()} ${await response.text()}`).toBe(true);
  return (response.status() === 204 ? undefined : await response.json()) as T;
}

/** Solves a puzzle through the API: start, then complete with the solution. */
export async function solveViaApi(request: APIRequestContext, player: Pick<Player, 'token'>, puzzle: ApiPuzzle): Promise<void> {
  const attempt = await api<{ id: number }>(request, player, 'POST', '/api/attempts', { puzzleId: puzzle.id });
  await api(request, player, 'POST', `/api/attempts/${attempt.id}/complete`, { grid: puzzle.solution, mistakes: 0, activeMs: 95_000 });
}
