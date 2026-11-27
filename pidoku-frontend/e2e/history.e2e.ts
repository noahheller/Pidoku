import { expect, test } from '@playwright/test';
import { api, fill, newPlayer, solveViaApi, type ApiPuzzle } from './support.ts';

test("a friend's puzzle history leaves out unfinished puzzles, and opens one with their time to beat", async ({ browser, request }) => {
  // Bob solves a random easy through the API, and starts another he doesn't finish.
  const bob = await newPlayer(browser);
  const bobName = `Bob ${bob.uid.slice(-6)}`;
  const { id: bobId, friendCode } = await api<{ id: number; friendCode: string }>(request, bob, 'PATCH', '/api/players/me', { displayName: bobName });
  const solved = await api<ApiPuzzle>(request, bob, 'GET', '/api/puzzles/random?difficulty=easy');
  await solveViaApi(request, bob, solved);
  const started = await api<ApiPuzzle>(request, bob, 'GET', '/api/puzzles/random?difficulty=easy');
  await api(request, bob, 'POST', '/api/attempts', { puzzleId: started.id });

  // Alice, signed in with Google (other players' profiles need it), is his friend and opens his profile.
  const alice = await newPlayer(browser, { google: true });
  await api(request, alice, 'POST', '/api/friends', { friendCode });
  await alice.page.goto(`/players/${bobId}`);
  const history = alice.page.getByRole('list', { name: `Puzzles ${bobName} solved` });
  await expect(history.getByRole('listitem')).toHaveCount(1);
  await expect(history).toContainText('Random');

  // Nothing in common yet.
  const historySection = alice.page.getByRole('region', { name: 'Puzzle history' });
  await historySection.getByRole('tab', { name: 'In common' }).click();
  await expect(historySection).toContainText("You and your friends haven't solved any of");
  await historySection.getByRole('tab', { name: 'All' }).click();

  // His puzzle opens on a start screen with his time to beat, the board hidden.
  await history.getByRole('button', { name: new RegExp(`Try to beat ${bobName}`) }).click();
  await expect(alice.page).toHaveURL(new RegExp(`/p/${solved.shareCode}`));
  await expect(alice.page.getByText(new RegExp(`^${bobName} solved this in \\d+:\\d\\d\\. Can you beat it\\?$`))).toBeVisible();
  await expect(alice.page.getByRole('grid', { name: 'Pidoku board' })).toHaveCount(0);
  await alice.page.getByRole('button', { name: 'Start', exact: true }).click();
  await fill(alice.page, solved);
  await expect(alice.page.getByRole('heading', { name: 'Solved!' })).toBeVisible();

  // Back on his profile, her time is next to his, and the puzzle is one they have in common.
  await alice.page.goto(`/players/${bobId}`);
  await expect(history.getByRole('button')).toContainText(/You \d+:\d\d/);
  await expect(history.getByRole('button')).toContainText('Open puzzle');
  await historySection.getByRole('tab', { name: 'In common' }).click();
  await expect(history.getByRole('listitem')).toHaveCount(1);

  await alice.context.close();
  await bob.context.close();
});
