import { expect, test } from '@playwright/test';
import { newPlayer } from './support.ts';

test('the home screen leads to Friends and to You, and You to Account & privacy', async ({ browser }) => {
  const { page, context } = await newPlayer(browser);
  const home = page.getByRole('navigation', { name: 'Pidoku' });

  // Friends: the code to send, and a nudge to pick a name first.
  await page.goto('/');
  await home.getByRole('link', { name: 'Friends', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Friends' })).toBeVisible();
  await expect(page.getByLabel(/^Your friend code: /)).toBeVisible();
  await expect(page.getByRole('note')).toContainText('Friends see you as Anonymous.');

  // You: a guest is asked to sign in with Google.
  await page.getByRole('link', { name: 'Puzzles', exact: true }).click();
  await home.getByRole('link', { name: 'You', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'You' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Keep your progress' }).getByRole('button', { name: 'Sign in with Google' })).toBeVisible();

  // Account & privacy, from the You menu.
  await page.getByRole('navigation', { name: 'You' }).getByRole('link', { name: /^Account & privacy/ }).click();
  await expect(page.getByRole('heading', { level: 2, name: 'Who can see your profile' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Delete my account' })).toBeVisible();

  // Visibility is saved as soon as it's picked (the choices lock until it is).
  const publicChoice = page.getByRole('radio', { name: /^Public/ });
  await publicChoice.check();
  await expect(publicChoice).toBeEnabled();
  await page.reload();
  await expect(publicChoice).toBeChecked();

  // And the You menu shows it.
  await page.getByRole('link', { name: 'You', exact: true }).click();
  await expect(page.getByRole('link', { name: /^Your profile/ })).toContainText('Public');

  await context.close();
});
