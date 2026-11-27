/**
 * How the game looks to this player: board theme, ink colour, digit style and what plays on a
 * solve. Only the player who picked them ever sees them, so nothing here reaches the API. Most
 * options are a thank-you for supporters on Ko-fi; a few are free for everyone.
 *
 * Locked options are gated in the browser on the player's supporter badge (`supporterSince`
 * from `/api/players/me`). Getting around that only changes colours on one's own screen, so
 * the server doesn't need to know. Anything other players would see (a badge choice, say) would
 * need checking on the server instead.
 */

export type ThemeId = 'system' | 'light' | 'dark' | 'high-contrast' | 'chalkboard' | 'graph-paper' | 'blueprint' | 'pumpkin-pie' | 'sepia';
export type InkId = 'default' | 'teal' | 'green' | 'brown' | 'pencil';
export type DigitStyleId = 'handwritten' | 'print' | 'mono' | 'serif';
export type CelebrationId = 'none' | 'pi-cascade' | 'pie-burst' | 'wave';

export interface Cosmetics {
  theme: ThemeId;
  ink: InkId;
  digitStyle: DigitStyleId;
  celebration: CelebrationId;
}

export interface CosmeticOption<Id extends string> {
  id: Id;
  label: string;
  supporterOnly: boolean;
}

export interface ThemeOption extends CosmeticOption<ThemeId> {
  /** The daisyUI theme set as `data-theme`; null follows the system's light or dark setting. */
  daisyTheme: string | null;
}

export interface InkOption extends CosmeticOption<InkId> {
  /** The colour on light themes and on dark ones; null keeps the theme's own pen colour. */
  color: { light: string; dark: string } | null;
}

export const THEMES: readonly ThemeOption[] = [
  { id: 'system', label: 'System', supporterOnly: false, daisyTheme: null },
  { id: 'light', label: 'Light', supporterOnly: false, daisyTheme: 'light' },
  { id: 'dark', label: 'Dark', supporterOnly: false, daisyTheme: 'dark' },
  // Accessibility is never a perk.
  { id: 'high-contrast', label: 'High contrast', supporterOnly: false, daisyTheme: 'high-contrast' },
  { id: 'chalkboard', label: 'Chalkboard', supporterOnly: true, daisyTheme: 'chalkboard' },
  { id: 'graph-paper', label: 'Graph paper', supporterOnly: true, daisyTheme: 'graph-paper' },
  { id: 'blueprint', label: 'Blueprint', supporterOnly: true, daisyTheme: 'blueprint' },
  { id: 'pumpkin-pie', label: 'Pumpkin pie', supporterOnly: true, daisyTheme: 'pumpkin-pie' },
  { id: 'sepia', label: 'Sepia notebook', supporterOnly: true, daisyTheme: 'sepia' },
];

/**
 * No red, violet or amber: red marks mistakes, violet is notes mode and amber is a hint's
 * highlight, and a player's own digits mustn't look like any of them.
 */
export const INKS: readonly InkOption[] = [
  { id: 'default', label: 'Theme blue', supporterOnly: false, color: null },
  { id: 'teal', label: 'Teal', supporterOnly: true, color: { light: '#0b7285', dark: '#66d9e8' } },
  { id: 'green', label: 'Green', supporterOnly: true, color: { light: '#237032', dark: '#8ce99a' } },
  { id: 'brown', label: 'Brown', supporterOnly: true, color: { light: '#8a4b14', dark: '#e8b27d' } },
  { id: 'pencil', label: 'Pencil', supporterOnly: true, color: { light: '#5c636a', dark: '#ced4da' } },
];

export const DIGIT_STYLES: readonly CosmeticOption<DigitStyleId>[] = [
  { id: 'handwritten', label: 'Handwritten', supporterOnly: false },
  { id: 'print', label: 'Print', supporterOnly: true },
  { id: 'mono', label: 'Mono', supporterOnly: true },
  { id: 'serif', label: 'Serif', supporterOnly: true },
];

export const CELEBRATIONS: readonly CosmeticOption<CelebrationId>[] = [
  { id: 'none', label: 'None', supporterOnly: false },
  { id: 'pi-cascade', label: 'π cascade', supporterOnly: true },
  { id: 'pie-burst', label: 'Pie burst', supporterOnly: true },
  { id: 'wave', label: 'Write-in wave', supporterOnly: true },
];

export const DEFAULT_COSMETICS: Cosmetics = { theme: 'system', ink: 'default', digitStyle: 'handwritten', celebration: 'none' };

const OPTIONS: { [K in keyof Cosmetics]: readonly CosmeticOption<Cosmetics[K]>[] } = {
  theme: THEMES,
  ink: INKS,
  digitStyle: DIGIT_STYLES,
  celebration: CELEBRATIONS,
};

const KEYS = Object.keys(OPTIONS) as (keyof Cosmetics)[];

function option<K extends keyof Cosmetics>(key: K, id: unknown): CosmeticOption<Cosmetics[K]> | undefined {
  return (OPTIONS[key] as readonly CosmeticOption<Cosmetics[K]>[]).find((o) => o.id === id);
}

/**
 * What was saved in this browser, made safe: anything missing, unknown (an option since removed)
 * or not an object at all becomes the default.
 */
export function parseCosmetics(saved: unknown): Cosmetics {
  const result = { ...DEFAULT_COSMETICS };
  if (typeof saved !== 'object' || saved === null) return result;
  const record = saved as Record<string, unknown>;
  for (const key of KEYS) {
    const found = option(key, record[key]);
    if (found) (result as Record<keyof Cosmetics, string>)[key] = found.id;
  }
  return result;
}

/** What the game shows: the saved choices, with any supporter-only one back to its default unless the player is a supporter. */
export function resolveCosmetics(saved: Cosmetics, isSupporter: boolean): Cosmetics {
  if (isSupporter) return saved;
  const result = { ...saved };
  for (const key of KEYS) {
    if (option(key, saved[key])?.supporterOnly) (result as Record<keyof Cosmetics, string>)[key] = DEFAULT_COSMETICS[key];
  }
  return result;
}

/** Whether any saved choice needs the supporter badge, i.e. whether it's worth asking the API. */
export function hasLockedChoice(saved: Cosmetics): boolean {
  return KEYS.some((key) => option(key, saved[key])?.supporterOnly === true);
}

export function themeOption(id: ThemeId): ThemeOption {
  return THEMES.find((t) => t.id === id) ?? THEMES[0];
}

export function inkOption(id: InkId): InkOption {
  return INKS.find((i) => i.id === id) ?? INKS[0];
}

/** An ink as a CSS colour that follows the theme's light or dark scheme; null for the theme's own. */
export function inkColor(ink: InkOption): string | null {
  return ink.color && `light-dark(${ink.color.light}, ${ink.color.dark})`;
}

/** π's first 112 digits as one string, for the celebrations (game/rules.ts has the board's). */
export const PI_STREAM = '314159265358979323846264338327950288419716939937510582097494459230781640628620899862803482534211706798214808651';
