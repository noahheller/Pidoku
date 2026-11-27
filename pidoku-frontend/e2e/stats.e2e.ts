import { expect, test } from '@playwright/test';
import { api, newPlayer, solveViaApi, type ApiPuzzle } from './support.ts';

test('the stats page sums up solves per difficulty, and remembers its switches', async ({ browser, request }) => {
  const player = await newPlayer(browser);
  const { page, context } = player;
  // One random easy solved (through the API), with no mistakes.
  const puzzle = await api<ApiPuzzle>(request, player, 'GET', '/api/puzzles/random?difficulty=easy');
  await solveViaApi(request, player, puzzle);

  // From the You page's menu, where the row's name includes its line of detail.
  await page.goto('/me');
  await page.getByRole('link', { name: /^Your stats/ }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Your stats' })).toBeVisible();
  const easy = page.getByRole('list', { name: 'By difficulty' }).getByRole('listitem').filter({ hasText: 'easy' });
  await expect(easy).toContainText('1 solve');
  await expect(easy).toContainText('100%');

  // Dailies only: none yet.
  await page.getByRole('tab', { name: 'Dailies' }).click();
  await expect(page.getByText("You haven't solved a daily yet.")).toBeVisible();

  // Both switches are remembered.
  await page.getByLabel('Include assisted solves').check();
  await page.reload();
  await expect(page.getByRole('tab', { name: 'Dailies' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByLabel('Include assisted solves')).toBeChecked();
  await context.close();
});
