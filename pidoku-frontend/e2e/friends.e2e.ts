import { expect, test } from '@playwright/test';
import { api, dailies, newPlayer, solveViaApi, today } from './support.ts';

test('a friend added by code shows on the home card and the Friends leaderboard', async ({ browser, request }) => {
  const { hard } = await dailies(request);
  const alice = await newPlayer(browser);
  const bob = await newPlayer(browser);
  const bobName = `Bob ${bob.uid.slice(-6)}`;

  // Bob has a name and has solved today's hard daily (set up through the API).
  const { friendCode } = await api<{ friendCode: string }>(request, bob, 'PATCH', '/api/players/me', { displayName: bobName });
  await solveViaApi(request, bob, hard);

  // Alice adds him by his code.
  await alice.page.goto('/me');
  await alice.page.getByLabel('Add a friend by code').fill(friendCode);
  await alice.page.getByRole('button', { name: 'Add', exact: true }).click();
  await expect(alice.page.getByRole('list', { name: 'Your friends' })).toContainText(bobName);

  // Her home screen's hard card says a friend solved it.
  await alice.page.goto('/');
  await expect(alice.page.getByRole('region', { name: 'Today' }).getByRole('button', { name: /^Daily Hard/ })).toContainText('1 friend solved');

  // His solve is on the Everyone leaderboard. Checked as Bob, whose own row always shows (as
  // "Your place" if he's outside the top 50): the database outlives a run when the API is reused
  // locally, so other tests' solvers can push him out of Alice's top 50.
  await bob.page.goto(`/leaderboard/${today()}?difficulty=hard`);
  await expect(bob.page.getByRole('heading', { name: 'Leaderboards' })).toBeVisible();
  await expect(bob.page.getByRole('main')).toContainText(bobName);

  // And on Alice's Friends leaderboard, which only holds her and her friends.
  await alice.page.goto(`/leaderboard/${today()}?difficulty=hard`);
  await alice.page.getByRole('tab', { name: 'Friends' }).click();
  await expect(alice.page.getByRole('list', { name: /^Friends/ })).toContainText(bobName);

  await alice.context.close();
  await bob.context.close();
});

test('the leaderboard date picker moves between days', async ({ browser }) => {
  const { page, context } = await newPlayer(browser);
  await page.goto(`/leaderboard/${today()}`);
  await expect(page.getByRole('button', { name: 'Next day' })).toBeDisabled();

  await page.getByRole('button', { name: 'Previous day' }).click();
  await expect(page).toHaveURL(new RegExp(`/leaderboard/${today(-1)}`));
  await page.getByRole('button', { name: 'Next day' }).click();
  await expect(page).toHaveURL(new RegExp(`/leaderboard/${today()}`));

  await page.getByLabel('Show the leaderboards for').fill(today(-2));
  await expect(page).toHaveURL(new RegExp(`/leaderboard/${today(-2)}`));
  await context.close();
});
