import { expect, test, type APIRequestContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { api, dailies, enter, fill, newPlayer, openDaily, type Player } from './support.ts';

/**
 * Makes a player a supporter the way a real tip does: a code from /me/support, then Ko-fi's webhook with
 * that code in the message. The token is the API test profile's (src/test/resources/config).
 */
async function makeSupporter(request: APIRequestContext, player: Player): Promise<void> {
  const { code } = await api<{ code: string }>(request, player, 'POST', '/api/players/me/supporter-code');
  const payment = { verification_token: 'test-kofi-token', kofi_transaction_id: `e2e-${randomUUID()}`, message: code, type: 'Donation', is_public: false };
  const response = await request.post('/api/kofi/webhook', { form: { data: JSON.stringify(payment) } });
  expect(response.ok(), `webhook: ${response.status()}`).toBe(true);
}

test('supporter looks are locked without the badge, but high contrast is free', async ({ browser }) => {
  const { page, context } = await newPlayer(browser);
  await page.goto('/me/look');
  const html = page.locator('html');

  await page.getByRole('button', { name: 'Chalkboard (supporters only)' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Chalkboard is a thank-you for supporters' })).toBeVisible();
  // It points at how to support, on its own page.
  await page.getByRole('link', { name: 'How to support Pidoku' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Support Pidoku' })).toBeVisible();
  await page.goBack();
  await expect(html).not.toHaveAttribute('data-theme');

  await page.getByRole('button', { name: 'High contrast', exact: true }).click();
  await expect(html).toHaveAttribute('data-theme', 'high-contrast');
  await expect(page.getByRole('button', { name: 'High contrast', exact: true })).toHaveAttribute('aria-pressed', 'true');

  await context.close();
});

test('a supporter picks a theme, ink and digits, and they stay after a reload', async ({ browser, request }) => {
  const player = await newPlayer(browser);
  const { page, context } = player;
  await page.goto('/me/support');
  await expect(page.getByRole('button', { name: 'Get my supporter code' })).toBeVisible();
  await makeSupporter(request, player);
  await page.goto('/me/look');
  await expect(page.getByText('Thanks for supporting Pidoku!')).toBeVisible();

  await page.getByRole('button', { name: 'Chalkboard', exact: true }).click();
  await page.getByRole('button', { name: 'Teal', exact: true }).click();
  await page.getByRole('button', { name: 'Mono', exact: true }).click();

  // Each digit preview keeps its own style, whatever the page uses.
  const sample = (name: string) => page.getByRole('button', { name, exact: true }).locator('[data-digits]');
  const font = (name: string) => sample(name).evaluate((el) => ({ family: getComputedStyle(el).fontFamily, variation: getComputedStyle(el).fontVariationSettings }));
  expect(await font('Handwritten')).toEqual({ family: expect.not.stringContaining('Pidoku Mono'), variation: '"CASL" 1' });
  expect(await font('Print')).toEqual({ family: expect.not.stringContaining('Pidoku Mono'), variation: '"CASL" 0' });
  expect((await font('Mono')).family).toContain('Pidoku Mono');

  const html = page.locator('html');
  const expectLooks = async () => {
    await expect(html).toHaveAttribute('data-theme', 'chalkboard');
    await expect(html).toHaveAttribute('data-digits', 'mono');
    await expect(html).toHaveAttribute('style', /--ink: light-dark\(#0b7285, #66d9e8\)/);
  };
  await expectLooks();
  await page.goto('/');
  await expectLooks();
  await page.reload();
  await expectLooks();

  await context.close();
});

test("a supporter's celebration plays on a solve, and the result still shows", async ({ browser, request }) => {
  const { easy } = await dailies(request);
  const player = await newPlayer(browser);
  const { page, context } = player;
  await page.goto('/me/support');
  await expect(page.getByRole('button', { name: 'Get my supporter code' })).toBeVisible();
  await makeSupporter(request, player);
  await page.goto('/me/look');
  await expect(page.getByText('Thanks for supporting Pidoku!')).toBeVisible();
  await page.getByRole('button', { name: 'π cascade', exact: true }).click();

  await openDaily(page, 'Daily Easy');
  const empties = await fill(page, easy, easy.givens.filter((v) => v === 0).length - 1);
  const last = easy.givens.findIndex((v, i) => v === 0 && !empties.includes(i));
  await enter(page, last, easy.solution[last]);

  await expect(page.getByTestId('celebration')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Solved!' })).toBeVisible();
  // It goes by itself, and never covers the board for clicks.
  await expect(page.getByTestId('celebration')).toHaveCount(0, { timeout: 5_000 });

  await context.close();
});

test('reduced motion skips the celebration', async ({ browser, request }) => {
  const { medium } = await dailies(request);
  const player = await newPlayer(browser);
  const { page, context } = player;
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/me/support');
  await expect(page.getByRole('button', { name: 'Get my supporter code' })).toBeVisible();
  await makeSupporter(request, player);
  await page.goto('/me/look');
  await expect(page.getByText('Thanks for supporting Pidoku!')).toBeVisible();
  await page.getByRole('button', { name: 'Pie burst', exact: true }).click();

  await openDaily(page, 'Daily Medium');
  await fill(page, medium);
  await expect(page.getByRole('heading', { name: 'Solved!' })).toBeVisible();
  await expect(page.getByTestId('celebration')).toBeHidden();

  await context.close();
});
