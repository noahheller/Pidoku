import { expect, test } from '@playwright/test';
import { dailies, fill, newPlayer, openDaily, sentEvents, timerSeconds } from './support.ts';

test("solving today's easy daily shows the result, streak and share line, then the next daily", async ({ browser, request }) => {
  const { easy } = await dailies(request);
  const { page, context } = await newPlayer(browser);
  const events = sentEvents(page);

  await openDaily(page, 'Daily Easy');
  await fill(page, easy);

  await expect(page.getByRole('heading', { name: 'Solved!' })).toBeVisible();
  await expect(page.getByText('Streak')).toBeVisible();
  await expect(page.getByText('1 day', { exact: true })).toBeVisible();
  const share = page.getByLabel('Share line');
  await expect(share).toContainText('Pidoku #');
  await expect(share).toContainText(`/p/${easy.shareCode}?ref=share`);
  // The solve is on the leaderboard under it.
  await expect(page.getByRole('list', { name: /^Everyone/ })).toBeVisible();

  // Copying the share line counts as a share; solving isn't leaving a puzzle unfinished.
  await page.getByRole('button', { name: 'Copy', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Copied', exact: true })).toBeVisible();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toContain('?ref=share');
  await expect.poll(() => events.map((e) => e.type)).toContain('share');
  expect(events.find((e) => e.type === 'share')?.detail).toEqual({ method: 'copy', daily: true });

  await page.getByRole('button', { name: /^Next: Daily Medium/ }).click();
  await expect(page.getByRole('heading', { level: 1, name: /^Daily Medium/ })).toBeVisible();

  // Back home, the card says it's solved and the streak shows.
  await page.goto('/');
  await expect(page.getByRole('region', { name: 'Recent' }).getByRole('button', { name: /Daily Easy/ })).toContainText('Solved');
  expect(events.map((e) => e.type)).not.toContain('abandoned');
  await context.close();
});

test('the clock pauses while the player is away from the puzzle, and Recent offers to continue it', async ({ browser, request }) => {
  const { medium } = await dailies(request);
  const { page, context } = await newPlayer(browser);

  await openDaily(page, 'Daily Medium');
  await fill(page, medium, 2);
  await expect.poll(() => timerSeconds(page), { timeout: 10_000 }).toBeGreaterThanOrEqual(6);
  const events = sentEvents(page);
  const before = await timerSeconds(page);
  await page.getByRole('button', { name: 'Back to puzzles, pausing your clock' }).click();
  const away = Date.now();
  // Leaving an unfinished puzzle is counted, with how far they got.
  await expect.poll(() => events.find((e) => e.type === 'abandoned')?.detail).toMatchObject({ filled: 2, daily: true });

  const recent = page.getByRole('region', { name: 'Recent' }).getByRole('button', { name: /Daily Medium/ });
  await expect(recent).toContainText('Continue');
  await page.waitForTimeout(4_000);
  await recent.click();

  // Straight back to the board (it's started), the time where it was: the 4 s away didn't count.
  await expect(page.getByRole('grid', { name: 'Pidoku board' })).toBeVisible();
  expect((Date.now() - away) / 1000).toBeGreaterThan(4);
  expect(await timerSeconds(page)).toBeLessThanOrEqual(before + 1);
  await context.close();
});

test('opening a share link counts the visit, then drops ?ref=share from the address', async ({ browser, request }) => {
  const { hard } = await dailies(request);
  const { page, context } = await newPlayer(browser);
  const events = sentEvents(page);

  await page.goto(`/p/${hard.shareCode}?ref=share`);
  await expect(page.getByRole('heading', { level: 1, name: /^Daily Hard/ })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`/p/${hard.shareCode}$`));

  // A new visitor's visit waits for their account, which starting the puzzle makes.
  await page.getByRole('button', { name: 'Start', exact: true }).click();
  await expect.poll(() => events.find((e) => e.type === 'share_arrival')?.detail).toEqual({ shareCode: hard.shareCode, newVisitor: true });
  await context.close();
});
