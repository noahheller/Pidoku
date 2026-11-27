import { describe, expect, it } from 'vitest';
import { capitalize, formatPenalty } from './format';

describe('formatPenalty', () => {
  it('is empty for none, seconds under a minute, and minutes past that', () => {
    expect(formatPenalty(0)).toBe('');
    expect(formatPenalty(40_000)).toBe('+40s');
    expect(formatPenalty(90_000)).toBe('+1:30');
  });
});

describe('capitalize', () => {
  it('upper-cases the first letter only', () => {
    expect(capitalize('hard')).toBe('Hard');
    expect(capitalize('tied with Bob')).toBe('Tied with Bob');
    expect(capitalize('')).toBe('');
  });
});
