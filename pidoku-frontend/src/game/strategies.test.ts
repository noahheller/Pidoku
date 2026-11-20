import { describe, expect, it } from 'vitest';
import { strategyName } from './strategies';

describe('strategyName', () => {
  it("names the engine's techniques for players", () => {
    expect(strategyName('HIDDEN_SINGLE_BOX')).toBe('Hidden single (box)');
    expect(strategyName('X_WING')).toBe('X-Wing');
    expect(strategyName('INTERSECTION_REMOVAL')).toBe('Pointing and claiming');
  });

  it('turns a technique it has no name for into words', () => {
    expect(strategyName('SWORDFISH')).toBe('Swordfish');
    expect(strategyName('SIMPLE_COLOURING')).toBe('Simple colouring');
  });

  it('has nothing for no technique', () => {
    expect(strategyName(null)).toBeNull();
    expect(strategyName('')).toBeNull();
  });
});
