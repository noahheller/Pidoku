import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { PuzzleDef } from './puzzleFile';
import { addRecent, localEntries, recentList, type RecentEntry } from './recent';
import { saveProgress, saveResult } from './storage';

const puzzle = (id: string, dailyDate?: string): PuzzleDef => ({ id, name: id, difficulty: null, size: 9, givens: [], solution: null, score: null, hardestStrategy: null, ...(dailyDate ? { dailyDate } : {}) });
const entry = (id: string, lastPlayedAt: number, completed = false): RecentEntry => ({ puzzle: puzzle(id), completed, timeMs: null, autoNotesUsed: false, lastPlayedAt });
const ids = (entries: RecentEntry[]) => entries.map((e) => e.puzzle.id);

beforeEach(() => {
  const store = new Map<string, string>();
  vi.stubGlobal('localStorage', {
    get length() {
      return store.size;
    },
    key: (i: number) => [...store.keys()][i] ?? null,
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => void store.set(key, value),
    removeItem: (key: string) => void store.delete(key),
  });
});

describe('addRecent', () => {
  it('adds a puzzle at the end, moving it there if it was already on the list', () => {
    const { list } = addRecent([puzzle('a'), puzzle('b')], puzzle('a'));
    expect(list.map((p) => p.id)).toEqual(['b', 'a']);
  });

  it('drops the oldest past the limit and says which', () => {
    const { list, dropped } = addRecent([puzzle('a'), puzzle('b'), puzzle('c')], puzzle('d'), 3);
    expect(list.map((p) => p.id)).toEqual(['b', 'c', 'd']);
    expect(dropped.map((p) => p.id)).toEqual(['a']);
  });
});

describe('localEntries', () => {
  it('leaves out puzzles never started, and takes a solve time from the result', () => {
    saveProgress('started', { values: [], notes: [], mistakes: 0, elapsedMs: 4_000, completed: false, savedAt: 50 });
    saveProgress('solved', { values: [], notes: [], mistakes: 0, elapsedMs: 9_000, completed: true, savedAt: 60 });
    saveResult('solved', { elapsedMs: 8_500, late: false });
    expect(localEntries([puzzle('opened'), puzzle('started'), puzzle('solved')])).toEqual([
      { puzzle: puzzle('started'), completed: false, timeMs: 4_000, autoNotesUsed: false, lastPlayedAt: 50 },
      { puzzle: puzzle('solved'), completed: true, timeMs: 8_500, autoNotesUsed: false, lastPlayedAt: 60 },
    ]);
  });
});

describe('recentList', () => {
  it('puts unfinished puzzles first, each group newest first', () => {
    expect(ids(recentList(null, [entry('a', 1, true), entry('b', 2), entry('c', 3, true), entry('d', 4)]))).toEqual(['d', 'b', 'c', 'a']);
  });

  it("adds this browser's puzzles the server doesn't have, and keeps the newest", () => {
    const server = [entry('s1', 30), entry('s2', 10, true)];
    const local = [entry('s1', 5), entry('offline', 20, true), entry('old', 1, true)];
    expect(ids(recentList(server, local, 3))).toEqual(['s1', 'offline', 's2']);
  });

  it("keeps the list's order for puzzles without a time, newest played last", () => {
    expect(ids(recentList(null, [entry('a', 0), entry('b', 0)]))).toEqual(['b', 'a']);
  });
});
