import { loadPendingCompletions, savePendingCompletions, type PendingCompletion } from '../game/storage';
import { ApiError, completeAttempt, type Attempt } from './client';

/**
 * Tells the API a puzzle was solved, and keeps trying if it can't be told right now (offline, the
 * API down, sign-in failing): the solve is kept in localStorage and sent again by
 * `sendPendingCompletions`. The API ignores a completion it already has, so sending one twice is
 * harmless.
 *
 * The recorded time is the server's, taken when the completion arrives, so a solve sent late is
 * recorded as slower than it was. That's better than not recording it, and never flatters.
 */

/** Old solves are dropped beyond this, so storage can't grow without limit. */
const MAX_PENDING = 20;

/**
 * Completes the attempt `started` resolves to, with the solved grid. Does nothing if the attempt
 * never started (null): then the server has no start time to measure from.
 */
export async function recordCompletion(started: Promise<Attempt | null>, grid: number[], mistakes: number): Promise<void> {
  const attempt = await started;
  if (!attempt) return;
  const completion = { attemptId: attempt.id, grid, mistakes };
  if ((await send(completion)) === 'retry') {
    const others = loadPendingCompletions().filter((p) => p.attemptId !== completion.attemptId);
    savePendingCompletions([...others, completion].slice(-MAX_PENDING));
  }
}

let sending = false;

/** Sends the solves saved by `recordCompletion`, keeping the ones that still can't be sent. */
export async function sendPendingCompletions(): Promise<void> {
  // One run at a time; and with nothing pending, don't sign in just to find that out.
  if (sending || loadPendingCompletions().length === 0) return;
  sending = true;
  try {
    for (const completion of loadPendingCompletions()) {
      if ((await send(completion)) === 'retry') continue;
      // Re-read before saving: a game finished meanwhile may have added to the list.
      savePendingCompletions(loadPendingCompletions().filter((p) => p.attemptId !== completion.attemptId));
    }
  } finally {
    sending = false;
  }
}

async function send({ attemptId, grid, mistakes }: PendingCompletion): Promise<'sent' | 'rejected' | 'retry'> {
  try {
    await completeAttempt(attemptId, grid, mistakes);
    return 'sent';
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
