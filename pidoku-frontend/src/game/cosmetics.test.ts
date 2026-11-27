/// <reference types="node" />
import { readFileSync } from 'node:fs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CELEBRATIONS, DEFAULT_COSMETICS, DIGIT_STYLES, hasLockedChoice, INKS, inkColor, inkOption, parseCosmetics, PI_STREAM, resolveCosmetics, THEMES, type Cosmetics } from './cosmetics';
import { PI_DIGITS } from './rules';
import { clearPlayerCaches, loadCosmetics, loadSupporterFlag, saveCosmetics, saveSupporterFlag } from './storage';

const fancy: Cosmetics = { theme: 'chalkboard', ink: 'teal', digitStyle: 'mono', celebration: 'pi-cascade' };

describe('parseCosmetics', () => {
  it('keeps known choices', () => {
    expect(parseCosmetics(fancy)).toEqual(fancy);
  });

  it('falls back to the default for anything missing, unknown or not an object', () => {
    expect(parseCosmetics(null)).toEqual(DEFAULT_COSMETICS);
    expect(parseCosmetics('chalkboard')).toEqual(DEFAULT_COSMETICS);
    expect(parseCosmetics({ theme: 'neon', ink: 42, digitStyle: 'serif' })).toEqual({ ...DEFAULT_COSMETICS, digitStyle: 'serif' });
  });
});

describe('resolveCosmetics', () => {
  it('shows a supporter everything they chose', () => {
    expect(resolveCosmetics(fancy, true)).toEqual(fancy);
  });

  it('puts locked choices back to the default for anyone else', () => {
    expect(resolveCosmetics(fancy, false)).toEqual(DEFAULT_COSMETICS);
  });

  it('keeps the free choices for anyone', () => {
    const free: Cosmetics = { ...DEFAULT_COSMETICS, theme: 'high-contrast' };
    expect(resolveCosmetics(free, false)).toEqual(free);
    expect(resolveCosmetics({ ...fancy, theme: 'dark' }, false)).toEqual({ ...DEFAULT_COSMETICS, theme: 'dark' });
  });
});

describe('hasLockedChoice', () => {
  it('is only true when something needs the badge', () => {
    expect(hasLockedChoice(DEFAULT_COSMETICS)).toBe(false);
    expect(hasLockedChoice({ ...DEFAULT_COSMETICS, theme: 'high-contrast' })).toBe(false);
    expect(hasLockedChoice({ ...DEFAULT_COSMETICS, celebration: 'wave' })).toBe(true);
  });
});

describe('options', () => {
  it('has a free default in every group, and accessibility is free', () => {
    for (const [group, options] of [
      ['theme', THEMES],
      ['ink', INKS],
      ['digitStyle', DIGIT_STYLES],
      ['celebration', CELEBRATIONS],
    ] as const) {
      const ids: string[] = options.map((o) => o.id);
      expect(new Set(ids).size).toBe(ids.length);
      expect(options.find((o) => o.id === DEFAULT_COSMETICS[group])?.supporterOnly).toBe(false);
    }
    expect(THEMES.find((t) => t.id === 'high-contrast')?.supporterOnly).toBe(false);
  });

  it('defines every daisyUI theme it names in index.css', () => {
    // Read from disk: Vitest hands CSS imports over empty, ?raw too.
    const indexCss = readFileSync(new URL('../index.css', import.meta.url), 'utf8');
    for (const theme of THEMES) {
      if (theme.daisyTheme) expect(indexCss, theme.daisyTheme).toContain(`name: '${theme.daisyTheme}';`);
    }
  });

  it('turns an ink into a colour for light and dark themes', () => {
    expect(inkColor(inkOption('default'))).toBeNull();
    expect(inkColor(inkOption('teal'))).toBe('light-dark(#0b7285, #66d9e8)');
  });

  it('starts the π stream with the board’s digits', () => {
    expect(PI_STREAM.slice(0, PI_DIGITS.length)).toBe(PI_DIGITS.join(''));
  });
});

describe('storage', () => {
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

  it('saves the choices and the supporter flag', () => {
    expect(loadSupporterFlag()).toBe(false);
    saveCosmetics(fancy);
    saveSupporterFlag(true);
    expect(parseCosmetics(loadCosmetics())).toEqual(fancy);
    expect(loadSupporterFlag()).toBe(true);
  });

  it('forgets the supporter flag for another player, but keeps the choices', () => {
    saveCosmetics(fancy);
    saveSupporterFlag(true);
    clearPlayerCaches();
    expect(loadSupporterFlag()).toBe(false);
    expect(resolveCosmetics(parseCosmetics(loadCosmetics()), loadSupporterFlag())).toEqual(DEFAULT_COSMETICS);
  });
});
