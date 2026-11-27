import { useCallback } from 'react';
import { currentAccount, type Account } from '../api/auth';
import { fetchPlayer, type Player } from '../api/client';
import { useLoad } from '../api/useLoad';
import { clearPlayerCaches } from '../game/storage';
import { reloadCosmetics, setSupporter } from './cosmetics';

interface SignedIn {
  account: Account | null;
  player: Player;
}

export type PlayerState = { status: 'loading' } | ({ status: 'loaded' } & SignedIn) | { status: 'failed'; message: string };

/**
 * The player and their account, for the You pages (`/me` and the pages under it). Signs the player
 * in (anonymously) when opened, like starting a puzzle does, and refreshes their supporter badge.
 */
export function usePlayer() {
  const { state: load, reload, update } = useLoad<SignedIn>('me', async (signal) => {
    const [account, player] = await Promise.all([currentAccount(), fetchPlayer(signal)]);
    setSupporter(player.supporterSince !== null);
    return { account, player };
  });
  const state: PlayerState = load.status === 'loaded' ? { status: 'loaded', ...load.value } : load;

  const setPlayer = useCallback((player: Player) => update((s) => ({ ...s, player })), [update]);

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
      update((s) => (s.account ? { ...s, account: { ...s.account, isAnonymous: false } } : s));
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
    [playerChanged, setPlayer, update],
  );

  return { state, reload, setPlayer, afterSignIn, playerChanged };
}
