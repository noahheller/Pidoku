import { expect, test } from '@playwright/test';
import { api, fill, newPlayer, solveViaApi, type ApiPuzzle } from './support.ts';

test('a challenge link to a random puzzle shows the time to beat, and the result has its leaderboard', async ({ browser, request }) => {
  // The challenger solves a random easy through the API.
  const challenger = await newPlayer(browser);
  const name = `Ace ${challenger.uid.slice(-6)}`;
  const { friendCode } = await api<{ friendCode: string }>(request, challenger, 'PATCH', '/api/players/me', { displayName: name });
  const puzzle = await api<ApiPuzzle>(request, challenger, 'GET', '/api/puzzles/random?difficulty=easy');
  await solveViaApi(request, challenger, puzzle);

  // A friend of theirs opens the link: a start screen with the challenger's time to beat (a
  // friend's time comes first, so other runs' solves of the same puzzle don't get in the way), and
  // no board yet.
  const { page, context } = await newPlayer(browser);
  await page.goto('/me');
  await page.getByLabel('Add a friend by code').fill(friendCode);
  await page.getByRole('button', { name: 'Add', exact: true }).click();
  await expect(page.getByRole('list', { name: 'Your friends' })).toContainText(name);
  await page.goto(`/p/${puzzle.shareCode}?ref=share`);
  // Its time is a second or so: the API caps a claimed time at the time since the start.
  await expect(page.getByText(new RegExp(`^${name} solved this in \\d+:\\d\\d\\. Can you beat it\\?$`))).toBeVisible();
  await expect(page.getByRole('grid', { name: 'Pidoku board' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Start', exact: true }).click();
  await expect(page.getByRole('grid', { name: 'Pidoku board' })).toBeVisible();
  await fill(page, puzzle);

  // The result has the puzzle's leaderboard, with the challenger on it. (The phone and desktop
  // runs can draw the same random puzzle, so there may be more solves than these two.)
  await expect(page.getByRole('heading', { name: 'Solved!' })).toBeVisible();
  const board = page.getByRole('list', { name: /^Everyone/ });
  await expect(board).toContainText(name);

  // Reopened, it carries on rather than showing the challenge again.
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Solved!' })).toBeVisible();
  await context.close();
  await challenger.context.close();
});

