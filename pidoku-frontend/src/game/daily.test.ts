import { describe, expect, it } from 'vitest';
import { dailyNumber, LAUNCH_DATE, shareLine, shareUrl } from './daily';

describe('dailyNumber', () => {
  it('is 1 on launch day and counts days from there', () => {
    expect(dailyNumber(LAUNCH_DATE)).toBe(1);
    expect(dailyNumber('2026-11-18')).toBe(2);
    expect(dailyNumber('2027-11-17')).toBe(366);
  });

  it('counts across daylight saving changes and leap days', () => {
    expect(dailyNumber('2027-03-15')).toBe(dailyNumber('2027-03-14') + 1);
    expect(dailyNumber('2028-03-01')).toBe(dailyNumber('2028-02-28') + 2);
  });
});

describe('shareLine', () => {
  const daily = { difficulty: 'Hard', dailyDate: '2026-11-19', elapsedMs: 252_000, mistakes: 0, url: 'https://pidoku.web.app/p/89TU533C' };

  it('gives the daily number, difficulty, time, a tick and the link', () => {
    expect(shareLine(daily)).toBe('Pidoku #3 Hard 4:12 ✓\nhttps://pidoku.web.app/p/89TU533C');
  });

  it('counts mistakes instead of the tick', () => {
    expect(shareLine({ ...daily, mistakes: 2 })).toBe('Pidoku #3 Hard 4:12 2✗\nhttps://pidoku.web.app/p/89TU533C');
  });

  it('leaves out what a puzzle does not have', () => {
    expect(shareLine({ difficulty: 'Easy', dailyDate: null, elapsedMs: 65_000, mistakes: null, url: null })).toBe('Pidoku Easy 1:05');
  });
});

describe('shareUrl', () => {
  it('links to the puzzle on this site', () => {
    expect(shareUrl('89TU533C', 'https://pidoku.web.app')).toBe('https://pidoku.web.app/p/89TU533C');
  });
});
