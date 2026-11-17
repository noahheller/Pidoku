import { useCallback, useEffect, useState } from 'react';
import { fetchDaily, localDate, type DailyPuzzles } from './client';

export type DailyState =
  | { status: 'loading' }
  | { status: 'loaded'; daily: DailyPuzzles }
  | { status: 'failed'; message: string };

/** How often an open page checks whether the player's date has changed. */
const DATE_CHECK_MS = 60_000;

/**
 * Loads today's daily puzzles on mount, and again when the player's local date changes while the
 * page is open (left open past midnight), so it never keeps showing yesterday's. `retry` loads
 * them again.
 */
export function useDaily(): { state: DailyState; retry: () => void } {
  const [state, setState] = useState<DailyState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  const [today, setToday] = useState(() => localDate(new Date()));

  useEffect(() => {
    // Setting the same date again changes nothing, so this only re-renders at midnight.
    const check = () => setToday(localDate(new Date()));
    const timer = setInterval(check, DATE_CHECK_MS);
    // A sleeping laptop or a background tab can skip the timer; check on coming back too.
    document.addEventListener('visibilitychange', check);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', check);
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    fetchDaily(controller.signal)
      .then((daily) => setState({ status: 'loaded', daily }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setState({ status: 'failed', message: e instanceof Error ? e.message : String(e) });
      });
    return () => controller.abort();
  }, [attempt, today]);

  const retry = useCallback(() => {
    setState({ status: 'loading' });
    setAttempt((n) => n + 1);
  }, []);

  // Another date's puzzles are on their way out: show the loading state until today's arrive.
  const stale = state.status === 'loaded' && state.daily.date !== today;
  return { state: stale ? { status: 'loading' } : state, retry };
}
