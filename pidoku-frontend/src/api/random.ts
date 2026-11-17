import { fetchRandom, type Difficulty } from './client';
import type { PuzzleDef } from '../game/puzzleFile';

/** How many times to ask again for a puzzle this browser hasn't played, before settling for a repeat. */
const MAX_TRIES = 5;

export interface RandomPick {
  puzzle: PuzzleDef;
  /** Play it from scratch: wipe this browser's old progress and restart any unfinished attempt. */
  replay: boolean;
  /** Something to tell the player, e.g. that they've played every puzzle of this difficulty. */
  notice: string | null;
}

/**
 * A random puzzle the player hasn't played, when there is one.
 *
 * The API already skips puzzles the signed-in player has started. This also skips ones this
 * browser has progress on, which the API may not know about (a game played signed out, or whose
 * start never reached the server), by asking again a few times. Once the player has played them
 * all, they get one again, from scratch, and are told why.
 *
 * @param hasPlayed whether this browser has saved progress for a puzzle
 */
export async function pickRandom(difficulty: Difficulty, hasPlayed: (puzzle: PuzzleDef) => boolean, signal?: AbortSignal): Promise<RandomPick> {
  let last: PuzzleDef | null = null;
  for (let tries = 0; tries < MAX_TRIES; tries++) {
    const { puzzle, repeat } = await fetchRandom(difficulty, signal);
    if (repeat) return { puzzle, replay: true, notice: `You've played every ${difficulty} puzzle. Here's one again.` };
    if (!hasPlayed(puzzle)) return { puzzle, replay: false, notice: null };
    last = puzzle;
  }
  // The server thinks these are new, but this browser has played every one it sent.
  return { puzzle: last!, replay: true, notice: "You've played this one before. Here it is again." };
}
