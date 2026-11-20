import { describe, expect, it } from 'vitest';
import { addDays, clampDate, dailyLabel, dailyNumber, LAUNCH_DATE, nextUnsolved, shareLine, shareUrl } from './daily';

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

describe('dailyLabel', () => {
  it('numbers dates from launch day on, and leaves earlier ones unnumbered', () => {
    expect(dailyLabel('2026-11-18')).toBe('Pidoku #2 · ');
    expect(dailyLabel('2026-11-17')).toBe('Pidoku #1 · ');
    expect(dailyLabel('2026-11-16')).toBe('');
  });
});

describe('nextUnsolved', () => {
  const dailies = [{ id: 'easy' }, { id: 'medium' }, { id: 'hard' }];
  const solved = (...ids: string[]) => (id: string) => ids.includes(id);

  it('suggests the next harder daily', () => {
    expect(nextUnsolved(dailies, 'easy', solved('easy'))?.id).toBe('medium');
  });

  it('skips dailies already solved', () => {
    expect(nextUnsolved(dailies, 'easy', solved('easy', 'medium'))?.id).toBe('hard');
  });

  it('wraps round to an easier one after the hardest', () => {
    expect(nextUnsolved(dailies, 'hard', solved('hard', 'easy'))?.id).toBe('medium');
  });

  it('has nothing once every daily is solved', () => {
    expect(nextUnsolved(dailies, 'medium', solved('easy', 'medium', 'hard'))).toBeNull();
  });

  it("never suggests the one just played, even before it's saved as solved", () => {
    expect(nextUnsolved(dailies, 'easy', solved('medium', 'hard'))).toBeNull();
  });
});

describe('addDays', () => {
  it('moves across months, years and daylight saving changes', () => {
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01');
    expect(addDays('2027-01-01', -1)).toBe('2026-12-31');
    expect(addDays('2027-03-14', 1)).toBe('2027-03-15');
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
  });
});

describe('clampDate', () => {
  it('keeps a date within the range', () => {
    expect(clampDate('2026-11-01', LAUNCH_DATE, '2026-11-20')).toBe(LAUNCH_DATE);
    expect(clampDate('2026-12-25', LAUNCH_DATE, '2026-11-20')).toBe('2026-11-20');
    expect(clampDate('2026-11-18', LAUNCH_DATE, '2026-11-20')).toBe('2026-11-18');
  });
});
