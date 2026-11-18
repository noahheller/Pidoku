import type { SavedProgress } from './game';
import type { PuzzleDef } from './puzzleFile';

const PROGRESS_PREFIX = 'pidoku:progress:';
const CUSTOM_PUZZLES_KEY = 'pidoku:custom-puzzles';
const SETTINGS_KEY = 'pidoku:settings';
const PENDING_COMPLETIONS_KEY = 'pidoku:pending-completions';
const RESULT_PREFIX = 'pidoku:result:';
const STREAK_KEY = 'pidoku:streak';
const NAME_PROMPT_DISMISSED_KEY = 'pidoku:name-prompt-dismissed';
const GOOGLE_PROMPT_DISMISSED_KEY = 'pidoku:google-prompt-dismissed';
const LEADERBOARD_SCOPE_KEY = 'pidoku:leaderboard-scope';

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
  /** The game's own timer. Missing from solves saved before it was recorded. */
  activeMs?: number;
  /** When it was solved (Date.now()), so the API can take the delay off the time. Missing from solves saved before milestone 4. */
  solvedAt?: number;
}

export function loadPendingCompletions(): PendingCompletion[] {
  const pending = read<PendingCompletion[]>(PENDING_COMPLETIONS_KEY);
  return Array.isArray(pending) ? pending : [];
}

export function savePendingCompletions(pending: PendingCompletion[]): void {
  write(PENDING_COMPLETIONS_KEY, pending);
}

/** A solve as the server recorded it, kept so reopening a solved puzzle shows the recorded time. */
export interface RecordedResult {
  elapsedMs: number;
  late: boolean;
}

export function loadResult(puzzleId: string): RecordedResult | null {
  return read<RecordedResult>(RESULT_PREFIX + puzzleId);
}

export function saveResult(puzzleId: string, result: RecordedResult): void {
  write(RESULT_PREFIX + puzzleId, result);
}

export function clearResult(puzzleId: string): void {
  try {
    localStorage.removeItem(RESULT_PREFIX + puzzleId);
  } catch {
    // Storage disabled: there was nothing saved to clear.
  }
}

/** The streak as last fetched, shown on the home screen while it's fetched again. */
export interface CachedStreak {
  current: number;
  best: number;
  solvedToday: boolean;
  /** The player's local date when it was fetched (YYYY-MM-DD). */
  asOf: string;
}

export function loadStreak(): CachedStreak | null {
  return read<CachedStreak>(STREAK_KEY);
}

export function saveStreak(streak: CachedStreak): void {
  write(STREAK_KEY, streak);
}

/** Whether the player chose not to pick a display name when asked. */
export function isNamePromptDismissed(): boolean {
  return read<boolean>(NAME_PROMPT_DISMISSED_KEY) === true;
}

export function dismissNamePrompt(): void {
  write(NAME_PROMPT_DISMISSED_KEY, true);
}

/** Whether the player chose not to sign in with Google when it was suggested under a result. */
export function isGooglePromptDismissed(): boolean {
  return read<boolean>(GOOGLE_PROMPT_DISMISSED_KEY) === true;
}

export function dismissGooglePrompt(): void {
  write(GOOGLE_PROMPT_DISMISSED_KEY, true);
}

/** Which leaderboard the player last looked at: everyone's times or just friends'. */
export type LeaderboardScope = 'everyone' | 'friends';

export function loadLeaderboardScope(): LeaderboardScope {
  return read<LeaderboardScope>(LEADERBOARD_SCOPE_KEY) === 'friends' ? 'friends' : 'everyone';
}

export function saveLeaderboardScope(scope: LeaderboardScope): void {
  write(LEADERBOARD_SCOPE_KEY, scope);
}

/**
 * Forgets what belongs to the player rather than to this browser, after switching to another
 * account: its streak, and whether it said no to picking a name.
 */
export function clearPlayerCaches(): void {
  for (const key of [STREAK_KEY, NAME_PROMPT_DISMISSED_KEY, GOOGLE_PROMPT_DISMISSED_KEY]) {
    try {
      localStorage.removeItem(key);
    } catch {
      // Storage disabled: nothing was cached.
    }
  }
}

export function loadSettings(): Settings {
  return { ...DEFAULT_SETTINGS, ...read<Partial<Settings>>(SETTINGS_KEY) };
}

export function saveSettings(settings: Settings): void {
  write(SETTINGS_KEY, settings);
}
