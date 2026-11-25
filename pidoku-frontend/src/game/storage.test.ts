import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAllLocalData, loadHint, saveHint } from './storage';

describe('clearAllLocalData', () => {
  let store: Map<string, string>;

  beforeEach(() => {
    // Vitest runs these in Node, which has no localStorage.
    store = new Map();
    vi.stubGlobal('localStorage', {
      get length() {
        return store.size;
      },
      key: (i: number) => [...store.keys()][i] ?? null,
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => void store.set(key, value),
      removeItem: (key: string) => void store.delete(key),
    });
  });

  it('keeps the hint on screen per puzzle until it is forgotten', () => {
    const hint = { values: [3, 0], hint: { step: 1 as const, units: [{ type: 'row' as const, index: 0 }], hintsUsed: 1, hintPenaltyMs: 10_000 } };
    saveHint('api-7', hint);
    expect(loadHint('api-7')).toEqual(hint);
    expect(loadHint('api-8')).toBeNull();
    saveHint('api-7', null);
    expect(loadHint('api-7')).toBeNull();
  });

  it("removes every Pidoku key and leaves other sites' keys alone", () => {
    for (const key of ['pidoku:streak', 'pidoku:progress:abc', 'pidoku:has-account', 'firebase:authUser', 'other']) store.set(key, '1');
    clearAllLocalData();
    expect([...store.keys()]).toEqual(['firebase:authUser', 'other']);
  });

  it('does nothing when storage is disabled', () => {
    vi.stubGlobal('localStorage', {
      get length(): number {
        throw new Error('disabled');
      },
    });
    expect(() => clearAllLocalData()).not.toThrow();
  });
});
