import { expect, test } from '@playwright/test';
import { cell, dailies, fill, newPlayer, openDaily } from './support.ts';

test('a game in progress carries on on another device', async ({ browser, request }) => {
  const { easy } = await dailies(request);
  const laptop = await newPlayer(browser);
  const phone = await newPlayer(browser, { uid: laptop.uid });

  await openDaily(laptop.page, 'Daily Easy');
  const filled = await fill(laptop.page, easy, 3);
  // Leaving the game saves the board straight away.
  const saved = laptop.page.waitForResponse((r) => r.url().includes('/progress') && r.request().method() === 'PUT');
  await laptop.page.getByRole('button', { name: 'Back to puzzles, pausing your clock' }).click();
  expect((await saved).status()).toBe(204);

  await openDaily(phone.page, 'Daily Easy');
  await expect(phone.page.getByText('Carried on from where you left off on another device.')).toBeVisible();
  for (const i of filled) await expect(cell(phone.page, i)).toHaveAccessibleName(new RegExp(`: ${easy.solution[i]}$`));

  await laptop.context.close();
  await phone.context.close();
});
