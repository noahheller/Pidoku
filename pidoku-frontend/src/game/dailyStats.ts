import type { DailyStats, SummaryTime } from '../api/client';
import { formatTime } from './format';

/** What a daily's card says about who has solved it. */
export interface CardStats {
  /** e.g. "3 friends solved", "12 players solved" or "No one has solved it yet". */
  solved: string;
  /** The fastest of them, if anyone has solved it. */
  fastest: { name: string; time: string } | null;
}

/**
 * The line under a daily's card. With friends who've solved it, it's about them (and the player,
 * who counts for the fastest): racing friends is the point. Otherwise it's everyone's numbers.
 */
export function cardStats(stats: DailyStats): CardStats {
  const friends = stats.friendsSolved ?? 0;
  if (friends > 0) {
    return { solved: `${friends} ${friends === 1 ? 'friend' : 'friends'} solved`, fastest: describe(stats.friendsFastest) };
  }
  if (stats.solved === 0) return { solved: 'No one has solved it yet', fastest: null };
  return { solved: `${stats.solved} ${stats.solved === 1 ? 'player' : 'players'} solved`, fastest: describe(stats.fastest) };
}

function describe(time: SummaryTime | null): CardStats['fastest'] {
  if (!time) return null;
  return { name: time.you ? 'You' : (time.displayName ?? 'Anonymous'), time: formatTime(time.elapsedMs) };
}
