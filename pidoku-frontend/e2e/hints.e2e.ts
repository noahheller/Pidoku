import { expect, test } from '@playwright/test';
import { dailies, enter, fill, newPlayer, openDaily } from './support.ts';

test('a hint shows where to look, what to look for, then the answer, and each step adds time', async ({ browser, request }) => {
  const { hard } = await dailies(request);
  const { page, context } = await newPlayer(browser);

  await openDaily(page, 'Daily Hard');
  const hint = page.getByRole('status', { name: 'Hint' });
  const penalty = page.getByTitle('Penalty', { exact: true });

  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(hint).toContainText('Step 1 of 3: Look at');
  await expect(penalty).toContainText('+10s');

  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(hint).toContainText('Step 2 of 3');
  await hint.getByRole('button', { name: "What's this?" }).click();
  await expect(penalty).toContainText('+30s');

  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(hint).toContainText('Step 3 of 3');
  await expect(penalty).toContainText('+1:30');
  // The answer's cell is ringed on the board.
  const target = page.getByRole('gridcell', { name: /fill this cell/ });
  await expect(target).toHaveCount(1);

  // Asking again for an answer already shown costs nothing.
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(hint).toContainText('Step 3 of 3');
  await expect(penalty).toContainText('+1:30');

  // A wrong guess hides the hint; fixing it brings it back.
  const index = await page.getByRole('gridcell').evaluateAll((cells) => cells.findIndex((c) => /fill this cell/.test(c.getAttribute('aria-label') ?? '')));
  const wrong = [1, 2, 3, 4, 5, 6, 9].find((d) => d !== hard.solution[index])!;
  await enter(page, index, wrong);
  await expect(hint).toHaveCount(0);
  // Asking again meanwhile only says to fix it; the hint isn't lost.
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(hint).toContainText('Fix the red digits first');
  await page.getByRole('button', { name: 'Erase', exact: true }).click();
  await expect(hint).toContainText('Step 3 of 3');

  // A correct digit in the hinted area is what clears it.
  await enter(page, index, hard.solution[index]);
  await expect(hint).toHaveCount(0);

  await fill(page, hard);
  await expect(page.getByRole('heading', { name: 'Solved!' })).toBeVisible();
  await expect(page.getByText('Ranked as')).toBeVisible();
  await expect(page.getByLabel('Share line')).toContainText('+1:35 1✗ (3 hints)');
  // On the leaderboard, with the time added.
  const you = page.getByRole('list', { name: /^Everyone/ }).getByRole('listitem').filter({ hasText: '+1:35' });
  await expect(you.first()).toBeVisible();

  // Recent on the home screen shows it too.
  await page.goto('/');
  await expect(page.getByRole('region', { name: 'Recent' }).getByRole('button', { name: /Daily Hard/ })).toContainText('+1:35');
  await context.close();
});

test('there is no hint while the board has a wrong digit', async ({ browser, request }) => {
  const { medium } = await dailies(request);
  const { page, context } = await newPlayer(browser);

  await openDaily(page, 'Daily Medium');
  const empty = medium.givens.indexOf(0);
  const wrong = [1, 2, 3, 4, 5, 6, 9].find((d) => d !== medium.solution[empty])!;
  await enter(page, empty, wrong);

  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(page.getByRole('status', { name: 'Hint' })).toContainText('Fix the red digits first');
  // Only the mistake's 5 s, no hint charged.
  await expect(page.getByTitle('Penalty', { exact: true })).toContainText('+5s');
  await context.close();
});

test('restarting a puzzle starts hints again at step 1, keeping the penalty so far on a daily', async ({ browser, request }) => {
  await dailies(request);
  const { page, context } = await newPlayer(browser);
  page.on('dialog', (dialog) => void dialog.accept());

  await openDaily(page, 'Daily Easy');
  const hint = page.getByRole('status', { name: 'Hint' });
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(hint).toContainText('Step 1 of 3');
  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(hint).toContainText('Step 2 of 3');

  await page.getByRole('button', { name: 'Restart', exact: true }).click();
  await expect(hint).toHaveCount(0);

  await page.getByRole('button', { name: 'Hint', exact: true }).click();
  await expect(hint).toContainText('Step 1 of 3');
  // 10 + 20 s from before the restart, plus 10 s for this step.
  await expect(page.getByTitle('Penalty', { exact: true })).toContainText('+40s');
  await context.close();
});
