import { useCallback, useEffect, useEffectEvent, useState } from 'react';
import { errorMessage } from './client';

/** Data being fetched for the screen: still on its way, here, or what went wrong. */
export type Load<T> =
  | { status: 'loading' }
  | { status: 'loaded'; value: T }
  | { status: 'failed'; message: string; error: unknown };

const LOADING = { status: 'loading' } as const;

/**
 * Loads data for `key` with `load`: on mount, again whenever the key changes, and on `reload`.
 * A load that's no longer wanted is aborted. Each result is kept with the key it was loaded for,
 * so a new key shows `loading` straight away rather than the old key's data. A null key loads
 * nothing (and stays `loading`).
 *
 * `load` may change on every render; only a new key starts a new load. `update` changes the
 * loaded value, as long as the key it was loaded for is still current: e.g. appending a page.
 */
export function useLoad<T>(key: string | null, load: (signal: AbortSignal) => Promise<T>): { state: Load<T>; reload: () => void; update: (change: (value: T) => T) => void } {
  const [reloads, setReloads] = useState(0);
  const [result, setResult] = useState<{ key: string; reloads: number; state: Load<T> } | null>(null);
  const run = useEffectEvent(load);

  useEffect(() => {
    if (key === null) return;
    const controller = new AbortController();
    const settle = (state: Load<T>) => {
      if (!controller.signal.aborted) setResult({ key, reloads, state });
    };
    run(controller.signal).then(
      (value) => settle({ status: 'loaded', value }),
      (error: unknown) => settle({ status: 'failed', message: errorMessage(error), error }),
    );
    return () => controller.abort();
  }, [key, reloads]);

  const reload = useCallback(() => setReloads((n) => n + 1), []);
  const update = useCallback(
    (change: (value: T) => T) =>
      setResult((r) => (r && r.key === key && r.reloads === reloads && r.state.status === 'loaded' ? { ...r, state: { status: 'loaded', value: change(r.state.value) } } : r)),
    [key, reloads],
  );

  const current = result !== null && result.key === key && result.reloads === reloads;
  return { state: current ? result.state : LOADING, reload, update };
}
