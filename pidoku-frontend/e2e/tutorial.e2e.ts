import { expect, test, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { cell, enter, newPlayer } from './support.ts';

interface Step {
  guided: boolean;
  cell: number | null;
  digit: number;
}
const tutorial = JSON.parse(readFileSync(new URL('../src/tutorial/tutorial.json', import.meta.url), 'utf8')) as { steps: Step[] };
const [single, secondFive, secondOne] = tutorial.steps.filter((s) => s.guided);

const next = (page: Page) => page.getByRole('button', { name: 'Next', exact: true }).click();
const lesson = (page: Page, title: string) => expect(page.getByRole('heading', { level: 2, name: title })).toBeVisible();

test('a first-time player is shown the tutorial before their first puzzle, and can skip it', async ({ browser }) => {
  const { page, context } = await newPlayer(browser, { tutorial: true });
  await page.goto('/');
  await page.getByRole('region', { name: 'Today' }).getByRole('button', { name: /^Daily Easy/ }).click();

  await expect(page.getByRole('heading', { level: 1, name: 'How to play' })).toBeVisible();
  await page.getByRole('button', { name: 'Skip' }).click();
  await expect(page.getByRole('button', { name: 'Start', exact: true })).toBeVisible();

  // Seen once is enough.
  await page.goto('/');
  await page.getByRole('region', { name: 'Today' }).getByRole('button', { name: /^Daily Medium/ }).click();
  await expect(page.getByRole('button', { name: 'Start', exact: true })).toBeVisible();
  await context.close();
});

test('the tutorial walks through singles, the second 5, notes and mistakes to a solved board', async ({ browser }) => {
  const { page, context } = await newPlayer(browser, { tutorial: true });
  await page.goto('/tutorial');

  await lesson(page, 'Sudoku with the digits of π');
  await next(page);

  await lesson(page, 'Start with a single');
  await expect(cell(page, single.cell!)).toHaveAccessibleName(/highlighted: fill this cell/);
  // A wrong digit first: it's flagged, and Undo takes it back.
  await enter(page, single.cell!, single.digit === 1 ? 2 : 1);
  await expect(page.getByRole('alert')).toContainText("That's not it");
  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await enter(page, single.cell!, single.digit);

  await lesson(page, 'The twist: a second 5');
  await enter(page, secondFive.cell!, secondFive.digit);

  await lesson(page, 'Pencil in notes');
  await page.getByRole('button', { name: 'Notes off' }).click();
  const noteCells = page.getByRole('gridcell', { name: /: empty, highlighted$/ });
  await expect(noteCells).toHaveCount(2);
  for (const target of await noteCells.all()) {
    await target.click();
    await page.getByRole('button', { name: new RegExp(`^Note ${secondOne.digit},`) }).click();
  }

  await lesson(page, 'Watch your notes');
  await page.getByRole('button', { name: 'Notes on' }).click();
  await enter(page, secondOne.cell!, secondOne.digit);

  await lesson(page, 'Notes clear themselves');
  await next(page);
  await lesson(page, 'Mistakes and undo');
  await next(page);

  await lesson(page, 'Finish it off');
  await page.getByRole('button', { name: 'Show me' }).click();
  await expect(page.getByRole('gridcell', { name: /highlighted: fill this cell/ })).toHaveCount(1);
  for (const step of tutorial.steps.filter((s) => !s.guided && s.cell !== null)) await enter(page, step.cell!, step.digit);

  await lesson(page, "That's Pidoku");
  await expect(page.getByText('without a mistake')).toHaveCount(0);
  await page.getByRole('button', { name: /Play today's puzzles/ }).click();
  await expect(page.getByRole('region', { name: 'Today' })).toBeVisible();
  await context.close();
});
