import { useCallback, useEffect, useState } from 'react';
import { currentAccount, type Account } from '../api/auth';
import { fetchPlayer, type Player } from '../api/client';
import { clearPlayerCaches } from '../game/storage';
import { reloadCosmetics, setSupporter } from './cosmetics';

export type PlayerState =
  | { status: 'loading' }
  | { status: 'loaded'; account: Account | null; player: Player }
  | { status: 'failed'; message: string };

/**
 * The player and their account, for the You pages (`/me` and the pages under it). Signs the player
 * in (anonymously) when opened, like starting a puzzle does, and refreshes their supporter badge.
 */
export function usePlayer() {
  const [state, setState] = useState<PlayerState>({ status: 'loading' });
  const [reloads, setReloads] = useState(0);
  const reload = useCallback(() => {
    setState({ status: 'loading' });
    setReloads((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([currentAccount(), fetchPlayer(controller.signal)])
      .then(([account, player]) => {
        setState({ status: 'loaded', account, player });
        setSupporter(player.supporterSince !== null);
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ status: 'failed', message: e instanceof Error ? e.message : String(e) });
      });
    return () => controller.abort();
  }, [reloads]);

  const setPlayer = useCallback((player: Player) => setState((s) => (s.status === 'loaded' ? { ...s, player } : s)), []);

  /** Another player is signed in here now (signed out, or a Google account that was already a player): what was cached for the old one no longer applies. */
  const playerChanged = useCallback(() => {
    clearPlayerCaches();
    reloadCosmetics();
    reload();
  }, [reload]);

  /** After "Sign in with Google" (see GoogleButton). */
  const afterSignIn = useCallback(
    (result: 'linked' | 'switched') => {
      if (result === 'switched') {
        // The Google account was already a player elsewhere: this browser uses that one now,
        // with this browser's anonymous games moved into it.
        playerChanged();
        return;
      }
      setState((s) => (s.status === 'loaded' && s.account ? { ...s, account: { ...s.account, isAnonymous: false } } : s));
      // A player with no name takes their Google account's on the next request: show it.
      fetchPlayer()
        .then((player) => {
          setPlayer(player);
          setSupporter(player.supporterSince !== null);
        })
        .catch(() => {
          // The old name stays on screen until the page is opened again.
        });
    },
    [playerChanged, setPlayer],
  );

  return { state, reload, setPlayer, afterSignIn, playerChanged };
}
