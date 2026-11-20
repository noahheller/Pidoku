import { describe, expect, it } from 'vitest';
import type { DailyStats } from '../api/client';
import { cardStats } from './dailyStats';

const stats = (overrides: Partial<DailyStats> = {}): DailyStats => ({
  difficulty: 'hard',
  solved: 12,
  fastest: { displayName: 'Sam', elapsedMs: 178_000, you: false },
  friendsSolved: null,
  friendsFastest: null,
  ...overrides,
});

describe('cardStats', () => {
  it("shows everyone's numbers to a signed-out player", () => {
    expect(cardStats(stats())).toEqual({ solved: '12 players solved', fastest: { name: 'Sam', time: '2:58' } });
  });

  it('is about friends once a friend has solved it', () => {
    const withFriends = stats({ friendsSolved: 3, friendsFastest: { displayName: 'Alex', elapsedMs: 192_000, you: false } });
    expect(cardStats(withFriends)).toEqual({ solved: '3 friends solved', fastest: { name: 'Alex', time: '3:12' } });
  });

  it('calls the player "You" when they are the fastest', () => {
    const yours = stats({ friendsSolved: 1, friendsFastest: { displayName: 'Ada', elapsedMs: 150_000, you: true } });
    expect(cardStats(yours).fastest?.name).toBe('You');
  });

  it("falls back to everyone's numbers when no friend has solved it", () => {
    expect(cardStats(stats({ friendsSolved: 0 })).solved).toBe('12 players solved');
  });

  it('says so when no one has solved it', () => {
    expect(cardStats(stats({ solved: 0, fastest: null }))).toEqual({ solved: 'No one has solved it yet', fastest: null });
  });

  it('uses the singular for one', () => {
    expect(cardStats(stats({ solved: 1 })).solved).toBe('1 player solved');
    expect(cardStats(stats({ friendsSolved: 1, friendsFastest: null })).solved).toBe('1 friend solved');
  });

  it('names a player without a display name Anonymous', () => {
    expect(cardStats(stats({ fastest: { displayName: null, elapsedMs: 60_000, you: false } })).fastest?.name).toBe('Anonymous');
  });
});
