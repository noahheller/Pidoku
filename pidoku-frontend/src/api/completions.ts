import { loadPendingCompletions, savePendingCompletions, type PendingCompletion } from '../game/storage';
import { ApiError, completeAttempt, type Attempt } from './client';

/**
 * Tells the API a puzzle was solved, and keeps trying if it can't be told right now (offline, the
 * API down, sign-in failing): the solve is kept in localStorage and sent again by
 * `sendPendingCompletions`. The API ignores a completion it already has, so sending one twice is
 * harmless.
 *
 * The server times the solve from the attempt's start to when the completion arrives. A solve sent
 * later also says how long ago it happened (`lateByMs`), which the server takes off and marks the
 * solve as late, so a dropped connection doesn't cost the player their time.
 */

/** Old solves are dropped beyond this, so storage can't grow without limit. */
const MAX_PENDING = 20;

/** What the game knows when the puzzle is solved. */
export interface Solve {
  grid: number[];
  mistakes: number;
  /** The game's own timer, which pauses while the tab is hidden. */
  activeMs: number;
}

/**
 * Completes the attempt `started` resolves to, with the solved grid. Does nothing if the attempt
 * never started (null): then the server has no start time to measure from.
 *
 * @returns the completed attempt, with the server's time; null if it couldn't be sent now (it's
 *          kept and sent later), was refused, or never started
 */
export async function recordCompletion(started: Promise<Attempt | null>, solve: Solve, now: () => number = Date.now): Promise<Attempt | null> {
  const solvedAt = now();
  const attempt = await started;
  if (!attempt) return null;
  const completion: PendingCompletion = { attemptId: attempt.id, ...solve, solvedAt };
  const result = await send(completion);
  if (result === 'retry') {
    const others = loadPendingCompletions().filter((p) => p.attemptId !== completion.attemptId);
    savePendingCompletions([...others, completion].slice(-MAX_PENDING));
  }
  return typeof result === 'object' ? result : null;
}

let sending = false;

/** Sends the solves saved by `recordCompletion`, keeping the ones that still can't be sent. */
export async function sendPendingCompletions(now: () => number = Date.now): Promise<void> {
  // One run at a time; and with nothing pending, don't sign in just to find that out.
  if (sending || loadPendingCompletions().length === 0) return;
  sending = true;
  try {
    for (const completion of loadPendingCompletions()) {
      // Older saved solves have no solvedAt; they're sent as before and timed until they arrive.
      const lateByMs = completion.solvedAt === undefined ? undefined : Math.max(0, now() - completion.solvedAt);
      if ((await send(completion, lateByMs)) === 'retry') continue;
      // Re-read before saving: a game finished meanwhile may have added to the list.
      savePendingCompletions(loadPendingCompletions().filter((p) => p.attemptId !== completion.attemptId));
    }
  } finally {
    sending = false;
  }
}

/** @param lateByMs left out for a completion sent as soon as the puzzle was solved */
async function send({ attemptId, grid, mistakes, activeMs }: PendingCompletion, lateByMs?: number): Promise<Attempt | 'rejected' | 'retry'> {
  try {
    return await completeAttempt(attemptId, { grid, mistakes, activeMs, lateByMs });
  } catch (e) {
    return isPermanent(e) ? 'rejected' : 'retry';
  }
}

/**
 * Whether the API has refused this completion for good (an unknown attempt, a grid that isn't
 * the solution), as opposed to not being reachable or not accepting the sign-in right now.
 */
function isPermanent(e: unknown): boolean {
  return e instanceof ApiError && e.status !== null && e.status >= 400 && e.status < 500 && e.status !== 401 && e.status !== 408 && e.status !== 429;
}
