import { read, remove, write } from '../game/storage';
import { isRefusedForGood, postEvents, type ApiEvent } from './client';

/**
 * Analytics events (see "Analytics" in docs/ROADMAP.md): a few things `attempts` doesn't record,
 * such as share taps and puzzles left unfinished. They carry the player's id only.
 *
 * Events are queued in localStorage and sent in batches with the account this browser already has,
 * so recording one never signs anyone in: a first-time visitor's share-link visit waits until
 * they start a puzzle (which signs them in), then goes with `occurredAt` saying when it happened.
 */

export type EventType = ApiEvent['type'];

const QUEUE_KEY = 'pidoku:pending-events';
/** Oldest events are dropped beyond this, so a browser that never signs in can't fill its storage. */
const MAX_QUEUED = 50;

function loadQueue(): ApiEvent[] {
  const queue = read<ApiEvent[]>(QUEUE_KEY);
  return Array.isArray(queue) ? queue : [];
}

/** Keeps the newest events. With storage full or disabled they're lost, which analytics can live with. */
function saveQueue(queue: ApiEvent[]): void {
  if (queue.length === 0) remove(QUEUE_KEY);
  else write(QUEUE_KEY, queue.slice(-MAX_QUEUED));
}

/**
 * Records an event: queued, then sent with whatever else is waiting.
 *
 * @param keepalive the page is closing: send with the token at hand, in a request that outlives it
 */
export function recordEvent(type: EventType, { puzzleId, detail }: Pick<ApiEvent, 'puzzleId' | 'detail'> = {}, keepalive = false, now: Date = new Date()): void {
  const event: ApiEvent = { type, occurredAt: now.toISOString() };
  if (puzzleId !== undefined) event.puzzleId = puzzleId;
  if (detail) event.detail = detail;
  saveQueue([...loadQueue(), event]);
  void flushEvents(keepalive);
}

let flushing: Promise<void> | null = null;

/**
 * Sends the queued events, if this browser has an account to send them for. Kept if they can't be
 * sent now; dropped if the API refuses them for good (a 4xx other than sign-in), so one bad event
 * can't block the queue.
 */
export function flushEvents(keepalive = false): Promise<void> {
  // A keepalive flush can't wait for one already running: the page is going away.
  if (flushing && !keepalive) return flushing;
  const run = (async () => {
    const batch = loadQueue();
    if (batch.length === 0) return;
    try {
      if (!(await postEvents(batch, keepalive))) return;
    } catch (e) {
      if (!isRefusedForGood(e)) return;
    }
    // Sent (or refused for good): drop them, keeping any recorded meanwhile.
    const sent = new Set(batch.map((e) => JSON.stringify(e)));
    saveQueue(loadQueue().filter((e) => !sent.has(JSON.stringify(e))));
  })();
  if (keepalive) return run;
  flushing = run.finally(() => {
    flushing = null;
  });
  return flushing;
}
