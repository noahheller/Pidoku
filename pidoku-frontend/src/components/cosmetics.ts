import monoFontUrl from '@fontsource-variable/recursive/files/recursive-latin-mono-normal.woff2?url';
import { useSyncExternalStore } from 'react';
import { inkColor, inkOption, parseCosmetics, resolveCosmetics, themeOption, type Cosmetics } from '../game/cosmetics';
import { loadCosmetics, loadSupporterFlag, saveCosmetics, saveSupporterFlag } from '../game/storage';

/**
 * The player's cosmetics for the whole app: what they chose (kept in localStorage), whether
 * they're a supporter (as the API last said), and the result applied to `<html>`. The theme is
 * `data-theme`, the digit style `data-digits` (see index.css), and the ink colour the `--ink`
 * variable the board reads.
 */
interface CosmeticsState {
  /** What the player chose, including options they can't use right now. */
  chosen: Cosmetics;
  supporter: boolean;
  /** What's shown: `chosen` with any locked option back to its default. */
  active: Cosmetics;
}

let state: CosmeticsState = initialState();
const listeners = new Set<() => void>();

function initialState(): CosmeticsState {
  const chosen = parseCosmetics(loadCosmetics());
  const supporter = loadSupporterFlag();
  return { chosen, supporter, active: resolveCosmetics(chosen, supporter) };
}

function update(chosen: Cosmetics, supporter: boolean): void {
  state = { chosen, supporter, active: resolveCosmetics(chosen, supporter) };
  applyCosmetics(state.active);
  for (const listener of listeners) listener();
}

/** Picks an option. A locked one is saved too, and shows if the player becomes a supporter. */
export function chooseCosmetic<K extends keyof Cosmetics>(key: K, value: Cosmetics[K]): void {
  const chosen = { ...state.chosen, [key]: value };
  saveCosmetics(chosen);
  update(chosen, state.supporter);
}

/** What `/api/players/me` says now: `supporterSince` set or not. */
export function setSupporter(supporter: boolean): void {
  if (supporter === state.supporter) return;
  saveSupporterFlag(supporter);
  update(state.chosen, supporter);
}

/** Back to what's in localStorage, after it was cleared (a deleted account, another player). */
export function reloadCosmetics(): void {
  state = initialState();
  applyCosmetics(state.active);
  for (const listener of listeners) listener();
}

export function currentCosmetics(): CosmeticsState {
  return state;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useCosmetics(): CosmeticsState {
  return useSyncExternalStore(subscribe, currentCosmetics, currentCosmetics);
}

/** Puts the active cosmetics on `<html>`. Called before the first render too, so there's no flash. */
export function applyCosmetics(active: Cosmetics = state.active, root: HTMLElement = document.documentElement): void {
  const theme = themeOption(active.theme).daisyTheme;
  if (theme) root.dataset.theme = theme;
  else delete root.dataset.theme;

  if (active.digitStyle === 'handwritten') delete root.dataset.digits;
  else root.dataset.digits = active.digitStyle;
  if (active.digitStyle === 'mono') loadMonoFont();

  const ink = inkColor(inkOption(active.ink));
  if (ink) root.style.setProperty('--ink', ink);
  else root.style.removeProperty('--ink');
}

let monoFont: Promise<unknown> | null = null;

/**
 * Recursive's monospace cut, only fetched once someone wants it (it's about 70 KB). It gets its
 * own family name: the npm package's mono.css reuses "Recursive Variable", which would replace
 * the casual cut the handwritten digits use.
 */
export function loadMonoFont(): void {
  if (monoFont || typeof FontFace === 'undefined' || typeof document === 'undefined') return;
  const face = new FontFace('Pidoku Mono', `url(${monoFontUrl}) format('woff2')`, { weight: '300 1000', display: 'swap' });
  document.fonts.add(face);
  monoFont = face.load().catch(() => {
    // Offline or blocked: the digits fall back to the handwritten font.
    monoFont = null;
  });
}
