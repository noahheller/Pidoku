import { CircleAlert, Flame, Puzzle, RotateCw, Shuffle, Trophy, Users, X, Zap } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { DIFFICULTIES, fetchDailySummary, fetchStreak, localDate, type DailyStats, type Difficulty } from '../api/client';
import { pickRandom } from '../api/random';
import { useDaily } from '../api/useDaily';
import { cardStats } from '../game/dailyStats';
import { formatDate, formatTime } from '../game/format';
import type { PuzzleDef } from '../game/puzzleFile';
import { PI_DIGITS } from '../game/rules';
import { clearProgress, clearResult, loadProgress, loadStreak, saveStreak, type CachedStreak } from '../game/storage';
import { strategyName } from '../game/strategies';

/** How to open a puzzle (see App's routes). */
export interface PlayOptions {
  /** Start it from scratch, restarting any unfinished attempt's clock. */
  replay?: boolean;
  /** Shown above the board when it opens. */
  notice?: string | null;
}

interface PuzzlePickerProps {
  bundled: PuzzleDef[];
  custom: PuzzleDef[];
  onPlay: (puzzle: PuzzleDef, options?: PlayOptions) => void;
  onAddCustom: (puzzles: PuzzleDef[]) => void;
  onRemoveCustom: (id: string) => void;
}

export function PuzzlePicker({ bundled, custom, onPlay, onAddCustom, onRemoveCustom }: PuzzlePickerProps) {
  const daily = useDaily();
  const summary = useDailySummary(daily.state.status === 'loaded' ? daily.state.daily.date : null);

  return (
    <main className="mx-auto grid max-w-3xl gap-8 px-4 pt-8 pb-16">
      <header className="grid gap-4">
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-4xl font-bold">
            Pidoku <span className="text-primary">π</span>
          </h1>
          <Link to="/me" className="btn btn-sm border-base-300 bg-base-100 shadow-none hover:border-primary">
            <Users className="size-4" aria-hidden="true" /> You &amp; friends
          </Link>
        </div>
        <DigitRow />
        <p className="text-base-content/60">Sudoku with the digits of pi. Play today's puzzles and race your friends, or try a random one.</p>
      </header>

      <section aria-labelledby="today-title">
        <div className="mb-3 flex flex-wrap items-baseline gap-x-3">
          <h2 id="today-title" className="text-lg font-semibold">
            Today
          </h2>
          {daily.state.status === 'loaded' && (
            <>
              <span className="text-sm text-base-content/60">{formatDate(daily.state.daily.date)}</span>
              <Link to={`/leaderboard/${daily.state.daily.date}`} className="link link-primary flex items-center gap-1 text-sm">
                <Trophy className="size-4" aria-hidden="true" /> Leaderboards
              </Link>
            </>
          )}
          <StreakBadge />
        </div>
        {daily.state.status === 'loading' && <PuzzleListSkeleton count={3} />}
        {daily.state.status === 'loaded' && <PuzzleList puzzles={daily.state.daily.puzzles} onPlay={onPlay} summary={summary} />}
        {daily.state.status === 'failed' && (
          <div role="alert" className="alert alert-soft alert-warning">
            <CircleAlert className="size-5" aria-hidden="true" />
            <span>{daily.state.message}</span>
            <button type="button" className="btn btn-sm" onClick={daily.retry}>
              <RotateCw className="size-4" aria-hidden="true" /> Retry
            </button>
          </div>
        )}
      </section>

      <RandomPuzzle
        onPlay={(puzzle, options) => {
          // Also kept in "Your puzzles", so leaving it doesn't lose it.
          onAddCustom([puzzle]);
          onPlay(puzzle, options);
        }}
      />

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

      {custom.length > 0 && (
        <section>
          <h2 className="mb-3 text-lg font-semibold">Your puzzles</h2>
          <PuzzleList puzzles={custom} onPlay={onPlay} onRemove={onRemoveCustom} />
        </section>
      )}

      <footer className="flex justify-center text-sm text-base-content/50">
        <Link to="/privacy" className="link link-hover">
          Privacy
        </Link>
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

/** What's known about today's dailies' solves: loading, by difficulty, or nothing (it failed). */
type Summary = { status: 'loading' } | { status: 'loaded'; byDifficulty: Map<string, DailyStats> } | { status: 'failed' };

/** How many have solved each of the date's dailies and the fastest times, for the cards. */
function useDailySummary(date: string | null): Summary {
  const [summary, setSummary] = useState<Summary & { date?: string }>({ status: 'loading' });
  useEffect(() => {
    if (!date) return;
    const controller = new AbortController();
    fetchDailySummary(date, controller.signal)
      .then((dailies) => setSummary({ status: 'loaded', date, byDifficulty: new Map(dailies.map((d) => [d.difficulty, d])) }))
      .catch(() => {
        // The cards work without their numbers; leave them off.
        if (!controller.signal.aborted) setSummary({ status: 'failed', date });
      });
    return () => controller.abort();
  }, [date]);
  return summary.date === date ? summary : { status: 'loading' };
}

interface PuzzleListProps {
  puzzles: PuzzleDef[];
  onPlay: (p: PuzzleDef) => void;
  onRemove?: (id: string) => void;
  /** For dailies: who has solved each one. */
  summary?: Summary;
}

function PuzzleList({ puzzles, onPlay, onRemove, summary }: PuzzleListProps) {
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
            <Technique strategy={puzzle.hardestStrategy} />
            {summary && <SolvedBy summary={summary} difficulty={puzzle.difficulty} />}
            <ProgressBadge id={puzzle.id} />
          </button>
          {onRemove && (
            <button type="button" className="btn btn-circle btn-ghost btn-xs absolute top-2 right-2 text-base-content/60 hover:bg-error/10 hover:text-error" aria-label={`Remove ${puzzle.name}`} onClick={() => onRemove(puzzle.id)}>
              <X className="size-4" aria-hidden="true" />
            </button>
          )}
        </li>
      ))}
    </ul>
  );
}

/** The hardest technique the engine needed, e.g. "Hardest step: Hidden pair". */
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

/** "3 friends solved · Fastest Alex 3:12", or everyone's numbers when no friend has solved it. */
function SolvedBy({ summary, difficulty }: { summary: Summary; difficulty: string | null }) {
  if (summary.status === 'loading') return <span className="skeleton h-4 w-44" aria-hidden="true" />;
  const stats = summary.status === 'loaded' && difficulty ? summary.byDifficulty.get(difficulty.toLowerCase()) : undefined;
  if (!stats) return null;
  const { solved, fastest } = cardStats(stats);
  return (
    <span className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-sm text-base-content/70">
      <span className="flex items-center gap-1.5">
        <Users className="size-3.5 shrink-0" aria-hidden="true" /> {solved}
      </span>
      {fastest && (
        <span className="flex items-center gap-1">
          <Zap className="size-3.5 shrink-0 fill-warning text-warning" aria-hidden="true" />
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
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
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
            // Same outline as the puzzle cards: a light border that turns primary on hover.
            className="btn btn-sm border-base-300 bg-base-100 capitalize shadow-none transition hover:border-primary hover:shadow-md"
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

function ProgressBadge({ id }: { id: string }) {
  const progress = loadProgress(id);
  if (!progress) return <span className="text-xs font-semibold text-base-content/60">New</span>;
  if (progress.completed) return <span className="text-xs font-semibold text-success">Solved · {formatTime(progress.elapsedMs)}</span>;
  return <span className="text-xs font-semibold text-primary">In progress · {formatTime(progress.elapsedMs)}</span>;
}
