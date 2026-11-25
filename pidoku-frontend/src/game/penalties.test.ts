import { describe, expect, it } from 'vitest';
import { mistakePenaltyMs, penaltyMs } from './penalties';

describe('mistakePenaltyMs', () => {
  it('is 5 s times the mistakes squared, so each one costs more', () => {
    expect([0, 1, 2, 3, 4].map(mistakePenaltyMs)).toEqual([0, 5_000, 20_000, 45_000, 80_000]);
  });
});

describe('penaltyMs', () => {
  it('adds the hints', () => {
    expect(penaltyMs(2, 30_000)).toBe(50_000);
  });
});
