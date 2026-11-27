import { CircleAlert, Flame, GraduationCap, Puzzle, RotateCw, Shuffle, Timer, Trophy, UserRound, Users } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { DIFFICULTIES, errorMessage, fetchDailySummary, fetchRecent, fetchStreak, localDate, type DailyStats, type Difficulty } from '../api/client';
import { pickRandom } from '../api/random';
import { useDaily } from '../api/useDaily';
import { useLoad, type Load } from '../api/useLoad';
import { cardStats, solvedElsewhere } from '../game/dailyStats';
import { formatDate, formatPenalty, formatTime } from '../game/format';
import type { PuzzleDef } from '../game/puzzleFile';
import type { TimeToBeat } from '../game/leaderboardStats';
import { localEntries, recentList, serverEntries, type RecentEntry } from '../game/recent';
import { canAutoNote } from '../game/game';
import { PI_DIGITS } from '../game/rules';
import { clearProgress, clearResult, loadProgress, loadStreak, saveProgress, saveResult, saveStreak, type CachedStreak } from '../game/storage';
import { strategyName } from '../game/strategies';

/** How to open a puzzle (see App's routes). */
export interface PlayOptions {
  /** Start it from scratch, restarting any unfinished attempt's clock. */
  replay?: boolean;
  /** Shown above the board when it opens. */
  notice?: string | null;
  /** Someone's solve to race, e.g. from their puzzle history: the puzzle waits on a start screen showing it. */
  timeToBeat?: TimeToBeat | null;
}

interface PuzzlePickerProps {
  bundled: PuzzleDef[];
  /** The puzzles played in this browser, oldest first (see game/recent.ts). */
  recent: PuzzleDef[];
  onPlay: (puzzle: PuzzleDef, options?: PlayOptions) => void;
}

export function PuzzlePicker({ bundled, recent, onPlay }: PuzzlePickerProps) {
  const daily = useDaily();
  const summary = useDailySummary(daily.state.status === 'loaded' ? daily.state.value.date : null);

  return (
    <main className="mx-auto grid max-w-3xl gap-8 px-4 pt-8 pb-16">
      <header className="grid gap-4">
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-4xl font-bold">
            Pidoku <span className="text-primary">π</span>
          </h1>
          <nav className="flex flex-wrap justify-end gap-2" aria-label="Pidoku">
            <Link to="/tutorial" className="btn btn-plain btn-sm">
              <GraduationCap className="size-4" aria-hidden="true" /> How to play
            </Link>
            <Link to="/friends" className="btn btn-plain btn-sm">
              <Users className="size-4" aria-hidden="true" /> Friends
            </Link>
            <Link to="/me" className="btn btn-plain btn-sm">
              <UserRound className="size-4" aria-hidden="true" /> You
            </Link>
          </nav>
        </div>
        <DigitRow />
        <p className="text-base-content/60">Sudoku with the digits of pi. Play today's puzzles and race your friends, or try a random one.</p>
      </header>

      <section aria-labelledby="today-title">
        <div className="mb-3 flex flex-wrap items-center gap-x-3">
          <h2 id="today-title" className="text-lg font-semibold">
            Today
          </h2>
          {daily.state.status === 'loaded' && (
            <>
              <span className="text-sm text-base-content/60">{formatDate(daily.state.value.date)}</span>
              <Link to={`/leaderboard/${daily.state.value.date}`} className="link link-primary flex items-center gap-1 text-sm">
                <Trophy className="size-4" aria-hidden="true" /> Leaderboards
              </Link>
            </>
          )}
          <StreakBadge />
        </div>
        {daily.state.status === 'loading' && <PuzzleListSkeleton count={3} />}
        {daily.state.status === 'loaded' && <PuzzleList puzzles={daily.state.value.puzzles} onPlay={onPlay} summary={summary} />}
        {daily.state.status === 'failed' && (
          <div role="alert" className="alert alert-soft alert-warning">
            <CircleAlert className="size-5" aria-hidden="true" />
            <span>{daily.state.message}</span>
            <button type="button" className="btn btn-plain btn-sm" onClick={daily.retry}>
              <RotateCw className="size-4" aria-hidden="true" /> Retry
            </button>
          </div>
        )}
      </section>

      <RandomPuzzle onPlay={onPlay} />

      {/* Bundled puzzles are the offline fallback: only shown when the API can't be reached. */}
      {daily.state.status === 'failed' && (
        <section aria-labelledby="offline-title">
          <h2 id="offline-title" className="text-lg font-semibold">
            Offline puzzles
          </h2>
          <p className="mb-3 text-sm text-base-content/60">These come with the game, so they work without a connection.</p>
          <PuzzleList puzzles={bundled} onPlay={onPlay} />
        </section>
      )}

      <Recent local={recent} onPlay={onPlay} />

      <footer className="flex justify-center gap-2 text-sm text-base-content/50">
        <Link to="/privacy" className="link link-hover">
          Privacy
        </Link>
        <span aria-hidden="true">·</span>
        <a href="https://github.com/noahheller/Pidoku" target="_blank" rel="noopener noreferrer" className="link link-hover">
          GitHub
        </a>
      </footer>
    </main>
  );
}

const ROW_DIGITS = PI_DIGITS.slice(0, 9);
const REPEATED = new Set(ROW_DIGITS.filter((d, i) => ROW_DIGITS.indexOf(d) !== i));

/** One row of a Pidoku board, written in digit by digit when the page loads. */
function DigitRow() {
  return (
    <div
      className="grid w-full max-w-[30rem] grid-cols-9 overflow-hidden rounded-sm border-3 border-base-content bg-base-100"
      role="img"
      aria-label={`A row of Pidoku: ${ROW_DIGITS.join(' ')}`}
    >
      {ROW_DIGITS.map((d, i) => (
        <span
          key={i}
          className={[
            'grid aspect-square place-items-center text-2xl font-bold sm:text-3xl',
            'motion-safe:animate-write-in',
            i < 8 && (i % 3 === 2 ? 'border-r-2 border-r-base-content' : 'border-r border-r-base-300'),
            REPEATED.has(d) && 'bg-primary/20',
          ]
            .filter(Boolean)
            .join(' ')}
          style={{ animationDelay: `${120 + i * 70}ms` }}
        >
          {d}
        </span>
      ))}
    </div>
  );
}

/** What's known about today's dailies' solves, by difficulty. If it fails, the cards go without their numbers. */
type Summary = Load<Map<string, DailyStats>>;

/** How many have solved each of the date's dailies and the fastest times, for the cards. */
function useDailySummary(date: string | null): Summary {
  // Only loaded once there's a date (a null key loads nothing).
  return useLoad(date, async (signal) => new Map((await fetchDailySummary(date!, signal)).map((d) => [d.difficulty, d]))).state;
}

interface PuzzleListProps {
  puzzles: PuzzleDef[];
  onPlay: (p: PuzzleDef) => void;
  /** For dailies: who has solved each one. */
  summary?: Summary;
}

function PuzzleList({ puzzles, onPlay, summary }: PuzzleListProps) {
  // Dailies solved on another device: saved here as solved, so opening one shows its result.
  useEffect(() => {
    if (summary?.status !== 'loaded') return;
    for (const puzzle of puzzles) {
      const yours = statsFor(summary, puzzle.difficulty)?.yours;
      const progress = solvedElsewhere(puzzle, yours, loadProgress(puzzle.id));
      if (!progress || !yours) continue;
      saveProgress(puzzle.id, progress);
      saveResult(puzzle.id, { elapsedMs: yours.elapsedMs, late: yours.late });
    }
  }, [puzzles, summary]);

  return (
    <ul className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-3">
      {puzzles.map((puzzle) => (
        <li key={puzzle.id} className="relative">
          <button
            type="button"
            className="card card-border h-full w-full cursor-pointer gap-1 border-base-300 bg-base-100 p-4 text-left transition hover:border-primary hover:shadow-md motion-safe:hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-primary"
            onClick={() => onPlay(puzzle)}
          >
            <span className="text-lg font-semibold">{puzzle.name}</span>
            {!puzzle.dailyDate && <Technique strategy={puzzle.hardestStrategy} />}
            {summary && <SolvedBy summary={summary} difficulty={puzzle.difficulty} />}
            <ProgressBadge id={puzzle.id} solvedMs={summary && statsFor(summary, puzzle.difficulty)?.yours?.elapsedMs} />
          </button>
        </li>
      ))}
    </ul>
  );
}

/**
 * The hardest technique the engine needed, e.g. "Hardest step: Hidden pair". Not shown for
 * dailies: most players don't know the technique names, and the difficulty already says enough.
 */
function Technique({ strategy }: { strategy: string | null }) {
  const name = strategyName(strategy);
  if (!name) return null;
  return (
    <span className="flex items-center gap-1.5 text-sm text-base-content/70">
      <Puzzle className="size-3.5 shrink-0" aria-hidden="true" />
      <span>
        Hardest step: <span className="font-medium text-base-content">{name}</span>
      </span>
    </span>
  );
}

function statsFor(summary: Summary, difficulty: string | null): DailyStats | undefined {
  return summary.status === 'loaded' && difficulty ? summary.value.get(difficulty.toLowerCase()) : undefined;
}

/** "3 friends solved · Fastest Alex 3:12", or everyone's numbers when no friend has solved it. */
function SolvedBy({ summary, difficulty }: { summary: Summary; difficulty: string | null }) {
  if (summary.status === 'loading') return <span className="skeleton h-4 w-44" aria-hidden="true" />;
  const stats = statsFor(summary, difficulty);
  if (!stats) return null;
  const { solved, fastest } = cardStats(stats);
  return (
    <span className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-sm text-base-content/70">
      <span className="flex items-center gap-1.5">
        <Users className="size-3.5 shrink-0" aria-hidden="true" /> {solved}
      </span>
      {fastest && (
        <span className="flex items-center gap-1" title="Fastest time">
          <Timer className="size-3.5 shrink-0 text-warning" aria-hidden="true" />
          <span className="sr-only">Fastest:</span>
          <span className="max-w-32 truncate">{fastest.name}</span>
          <span className="font-semibold tabular-nums text-base-content">{fastest.time}</span>
        </span>
      )}
    </span>
  );
}

function PuzzleListSkeleton({ count }: { count: number }) {
  return (
    <ul className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-3" aria-busy="true" aria-label="Loading puzzles">
      {Array.from({ length: count }, (_, i) => (
        <li key={i} className="card card-border gap-2 border-base-300 bg-base-100 p-4">
          <span className="skeleton h-6 w-24" />
          <span className="skeleton h-4 w-40" />
          <span className="skeleton h-3 w-10" />
        </li>
      ))}
    </ul>
  );
}

/** One button per difficulty; each fetches a random pool puzzle and starts it. */
function RandomPuzzle({ onPlay }: { onPlay: (puzzle: PuzzleDef, options: PlayOptions) => void }) {
  const [loading, setLoading] = useState<Difficulty | null>(null);
  const [error, setError] = useState<string | null>(null);

  const play = (difficulty: Difficulty) => {
    setLoading(difficulty);
    setError(null);
    pickRandom(difficulty, (puzzle) => loadProgress(puzzle.id) !== null)
      .then(({ puzzle, replay, notice }) => {
        if (replay) {
          // A puzzle they've played: start it over rather than reopening the old, finished board.
          clearProgress(puzzle.id);
          clearResult(puzzle.id);
        }
        onPlay(puzzle, { replay, notice });
      })
      .catch((e: unknown) => setError(errorMessage(e)))
      .finally(() => setLoading(null));
  };

  return (
    <section aria-labelledby="random-title">
      <h2 id="random-title" className="text-lg font-semibold">
        Random puzzle
      </h2>
      <p className="mb-3 text-sm text-base-content/60">A puzzle you haven't played yet, at the difficulty you pick.</p>
      <div className="flex flex-wrap gap-2">
        {DIFFICULTIES.map((difficulty) => (
          <button
            key={difficulty}
            type="button"
            className="btn btn-plain btn-sm capitalize"
            disabled={loading !== null}
            onClick={() => play(difficulty)}
          >
            {loading === difficulty ? <span className="loading loading-spinner loading-xs" aria-hidden="true" /> : <Shuffle className="size-4" aria-hidden="true" />}
            {difficulty}
          </button>
        ))}
      </div>
      {error && (
        <div role="alert" className="mt-3 alert alert-soft alert-error">
          <CircleAlert className="size-5" aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}
    </section>
  );
}

/**
 * The player's streak, once they have one. Only players who've had a result shown have a cached
 * streak, so a first-time visitor isn't signed in just to find they have none; for everyone else
 * the cached one shows at once (if it's from today) while a fresh one is fetched.
 */
function StreakBadge() {
  const [streak, setStreak] = useState<CachedStreak | null>(() => {
    const cached = loadStreak();
    return cached?.asOf === localDate(new Date()) ? cached : null;
  });

  useEffect(() => {
    if (!loadStreak()) return;
    const controller = new AbortController();
    const now = new Date();
    fetchStreak(controller.signal, now)
      .then((fresh) => {
        const cached = { ...fresh, asOf: localDate(now) };
        saveStreak(cached);
        setStreak(cached);
      })
      .catch(() => {
        // Keep whatever was cached.
      });
    return () => controller.abort();
  }, []);

  if (!streak || streak.current === 0) return null;
  return (
    <span className="flex w-full items-center gap-1 text-sm font-semibold text-warning sm:ml-auto sm:w-auto">
      <Flame className="size-4" aria-hidden="true" />
      {streak.current}-day streak
      {!streak.solvedToday && <span className="font-normal text-base-content/60">· solve one today to keep it</span>}
    </span>
  );
}

/** @param solvedMs for a daily solved on any device, the solve's time (see DailyStats.yours) */
function ProgressBadge({ id, solvedMs }: { id: string; solvedMs?: number }) {
  const progress = loadProgress(id);
  if (!progress?.completed && solvedMs !== undefined) return <span className="text-xs font-semibold text-success">Solved · {formatTime(solvedMs)}</span>;
  if (!progress) return <span className="text-xs font-semibold text-base-content/60">New</span>;
  if (progress.completed) return <span className="text-xs font-semibold text-success">Solved · {formatTime(progress.elapsedMs)}</span>;
  return <span className="text-xs font-semibold text-primary">In progress · {formatTime(progress.elapsedMs)}</span>;
}

/**
 * The puzzles last played, unfinished first ("Continue"), then the latest solves with their times.
 * From the server for a browser that has signed in (the same list on every device, dailies
 * included), with this browser's own puzzles until it answers or if it can't be reached.
 */
function Recent({ local, onPlay }: { local: PuzzleDef[]; onPlay: (p: PuzzleDef) => void }) {
  const [server, setServer] = useState<RecentEntry[] | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    fetchRecent(controller.signal)
      .then((recent) => {
        if (recent) setServer(serverEntries(recent));
      })
      .catch(() => {
        // This browser's list stands in.
      });
    return () => controller.abort();
  }, []);

  const entries = recentList(server, localEntries(local));
  if (entries.length === 0) return null;
  return (
    <section aria-labelledby="recent-title">
      <h2 id="recent-title" className="mb-3 text-lg font-semibold">
        Recent
      </h2>
      <ul className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-3">
        {entries.map(({ puzzle, completed, timeMs, penaltyMs, autoNotesUsed }) => (
          <li key={puzzle.id}>
            <button
              type="button"
              className="card card-border h-full w-full cursor-pointer gap-1 border-base-300 bg-base-100 p-4 text-left transition hover:border-primary hover:shadow-md motion-safe:hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-primary"
              onClick={() => onPlay(puzzle)}
            >
              <span className="text-lg font-semibold">{puzzle.name}</span>
              {puzzle.dailyDate && <span className="text-sm text-base-content/60">{formatDate(puzzle.dailyDate)}</span>}
              {!puzzle.dailyDate && <Technique strategy={puzzle.hardestStrategy} />}
              {completed ? (
                <span className="text-xs font-semibold text-success">
                  Solved{timeMs !== null && ` · ${formatTime(timeMs)}`}
                  {timeMs !== null && penaltyMs > 0 && <span className="text-warning"> {formatPenalty(penaltyMs)}</span>}
                  {canAutoNote(puzzle) && <span className="font-normal text-base-content/60">{autoNotesUsed ? ' · Auto notes' : ' · No auto notes'}</span>}
                </span>
              ) : (
                <span className="text-xs font-semibold text-primary">
                  Continue{timeMs !== null && timeMs > 0 && ` · ${formatTime(timeMs)}`}
                  {timeMs !== null && timeMs > 0 && penaltyMs > 0 && <span className="text-warning"> {formatPenalty(penaltyMs)}</span>}
                </span>
              )}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
