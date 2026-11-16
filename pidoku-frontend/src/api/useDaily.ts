import { useCallback, useEffect, useState } from 'react';
import { fetchDaily, type DailyPuzzles } from './client';

export type DailyState =
  | { status: 'loading' }
  | { status: 'loaded'; daily: DailyPuzzles }
  | { status: 'failed'; message: string };

/** Loads today's daily puzzles once on mount; `retry` loads them again. */
export function useDaily(): { state: DailyState; retry: () => void } {
  const [state, setState] = useState<DailyState>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    fetchDaily(controller.signal)
      .then((daily) => setState({ status: 'loaded', daily }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        setState({ status: 'failed', message: e instanceof Error ? e.message : String(e) });
      });
    return () => controller.abort();
  }, [attempt]);

  const retry = useCallback(() => {
    setState({ status: 'loading' });
    setAttempt((n) => n + 1);
  }, []);

  return { state, retry };
}
