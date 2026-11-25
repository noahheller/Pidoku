import { describe, expect, it } from 'vitest';
import { DESCRIBED_STRATEGIES, strategyInfo, strategyName } from './strategies';
import strategyEnumSource from '../../../pidoku-engine/src/main/java/dev/noahheller/pidokuengine/strategy/StrategyEnum.java?raw';

/** The constants of pidoku-engine's StrategyEnum, read from its source. */
function engineStrategies(): string[] {
  return [...strategyEnumSource.matchAll(/^\s+([A-Z][A-Z_]+)\(/gm)].map((m) => m[1]);
}

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

describe('strategyInfo', () => {
  it("describes every technique the engine has", () => {
    const engine = engineStrategies();
    expect(engine.length).toBeGreaterThan(0);
    expect([...DESCRIBED_STRATEGIES].sort()).toEqual([...engine].sort());
  });

  it.each(DESCRIBED_STRATEGIES)('%s says what to look for without naming itself', (strategy) => {
    const info = strategyInfo(strategy)!;
    expect(info.lookFor).not.toBe('');
    expect(info.explanation).not.toBe('');
    const nameWords = info.name.replace(/\s*\(.*\)/, '').toLowerCase();
    expect(info.lookFor.toLowerCase()).not.toContain(nameWords);
  });

  it('has no descriptions for a technique it does not know', () => {
    expect(strategyInfo('SWORDFISH')).toEqual({ name: 'Swordfish', lookFor: '', explanation: '' });
  });
});
