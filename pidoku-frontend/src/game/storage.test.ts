import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAllLocalData } from './storage';

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
