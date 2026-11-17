import type { SavedProgress } from './game';
import type { PuzzleDef } from './puzzleFile';

const PROGRESS_PREFIX = 'pidoku:progress:';
const CUSTOM_PUZZLES_KEY = 'pidoku:custom-puzzles';
const SETTINGS_KEY = 'pidoku:settings';
const PENDING_COMPLETIONS_KEY = 'pidoku:pending-completions';

export interface Settings {
  showMistakes: boolean;
}

const DEFAULT_SETTINGS: Settings = { showMistakes: true };

function read<T>(key: string): T | null {
  try {
    const raw = localStorage.getItem(key);
    return raw === null ? null : (JSON.parse(raw) as T);
  } catch {
    return null;
  }
}

function write(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Storage full or disabled (private mode): the game still works, it just won't persist.
  }
}

export function loadProgress(puzzleId: string): SavedProgress | null {
  return read<SavedProgress>(PROGRESS_PREFIX + puzzleId);
}

export function saveProgress(puzzleId: string, progress: SavedProgress): void {
  write(PROGRESS_PREFIX + puzzleId, progress);
}

export function clearProgress(puzzleId: string): void {
  try {
    localStorage.removeItem(PROGRESS_PREFIX + puzzleId);
  } catch {
    // Storage disabled: there was nothing saved to clear.
  }
}

export function loadCustomPuzzles(): PuzzleDef[] {
  return read<PuzzleDef[]>(CUSTOM_PUZZLES_KEY) ?? [];
}

export function saveCustomPuzzles(puzzles: PuzzleDef[]): void {
  write(CUSTOM_PUZZLES_KEY, puzzles);
}

/** A solved puzzle the API hasn't been told about yet (see api/completions.ts). */
export interface PendingCompletion {
  attemptId: number;
  grid: number[];
  mistakes: number;
}

export function loadPendingCompletions(): PendingCompletion[] {
  const pending = read<PendingCompletion[]>(PENDING_COMPLETIONS_KEY);
  return Array.isArray(pending) ? pending : [];
}

export function savePendingCompletions(pending: PendingCompletion[]): void {
  write(PENDING_COMPLETIONS_KEY, pending);
}

export function loadSettings(): Settings {
  return { ...DEFAULT_SETTINGS, ...read<Partial<Settings>>(SETTINGS_KEY) };
}

export function saveSettings(settings: Settings): void {
  write(SETTINGS_KEY, settings);
}
