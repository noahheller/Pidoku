import { ArrowLeft, BarChart3, CircleAlert, Flame, Lightbulb, RotateCw, Target, TrendingDown, TrendingUp, Trophy } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { fetchStats, type DifficultyStats, type PlayerStats, type StatsSource } from '../api/client';
import { formatTime } from '../game/format';
import { cleanPercent, formatMistakes, trendLine } from '../game/stats';
import { loadStatsIncludeAssisted, loadStatsSource, saveStatsIncludeAssisted, saveStatsSource } from '../game/storage';
import { Switch } from './Leaderboard';
import { Tile } from './PlayerPage';

type State = { status: 'loading' } | { status: 'loaded'; stats: PlayerStats } | { status: 'failed'; message: string };

const SOURCES: [StatsSource, string][] = [
  ['all', 'All'],
  ['daily', 'Dailies'],
  ['random', 'Random'],
];

/**
 * `/me/stats`: how the player does at each difficulty. Each puzzle counts once, by its first solve,
 * and times are ranked times (the game's clock plus penalties), as on the leaderboards. Two
 * switches, both remembered: which puzzles (all, dailies, random), and whether solves with hints or
 * auto notes count (off by default, so the averages compare like with like).
 */
export function StatsPage() {
  const [source, setSource] = useState<StatsSource>(loadStatsSource);
  const [includeAssisted, setIncludeAssisted] = useState(loadStatsIncludeAssisted);
  const [state, setState] = useState<State & { key?: string }>({ status: 'loading' });
  const [retries, setRetries] = useState(0);
  const key = `${source}/${includeAssisted}/${retries}`;

  useEffect(() => {
    const controller = new AbortController();
    fetchStats(source, includeAssisted, controller.signal)
      .then((stats) => setState({ status: 'loaded', stats, key }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ status: 'failed', message: e instanceof Error ? e.message : String(e), key });
      });
    return () => controller.abort();
  }, [source, includeAssisted, key]);

  const current: State = state.key === key ? state : { status: 'loading' };

  return (
    <main className="mx-auto grid max-w-xl gap-5 px-4 pt-4 pb-16">
      <header className="grid gap-3">
        <Link to="/me" className="btn btn-ghost btn-sm w-fit text-primary">
          <ArrowLeft className="size-4 shrink-0" aria-hidden="true" /> You
        </Link>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <BarChart3 className="size-6 text-primary" aria-hidden="true" /> Your stats
        </h1>
      </header>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <Switch
          label="Which puzzles"
          options={SOURCES}
          value={source}
          onChange={(s) => {
            setSource(s);
            saveStatsSource(s);
          }}
        />
        <label className="flex cursor-pointer items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="toggle toggle-sm toggle-primary"
            checked={includeAssisted}
            onChange={(e) => {
              setIncludeAssisted(e.target.checked);
              saveStatsIncludeAssisted(e.target.checked);
            }}
          />
          Include assisted solves
        </label>
      </div>
      <p className="-mt-3 text-xs text-base-content/60">
        Each puzzle's first solve counts. Times include the leaderboards' penalties for mistakes and hints.{' '}
        {includeAssisted ? 'Solves with hints or auto notes are counted.' : 'Solves with hints or auto notes are left out.'}
      </p>

      {current.status === 'loading' && (
        <p className="flex items-center gap-2 text-base-content/60" role="status">
          <span className="loading loading-spinner loading-sm" aria-hidden="true" /> Loading…
        </p>
      )}
      {current.status === 'failed' && (
        <div role="alert" className="alert alert-soft alert-warning flex flex-wrap">
          <CircleAlert className="size-5" aria-hidden="true" />
          <span>{current.message}</span>
          <button type="button" className="btn btn-sm" onClick={() => setRetries((n) => n + 1)}>
            <RotateCw className="size-4" aria-hidden="true" /> Retry
          </button>
        </div>
      )}
      {current.status === 'loaded' && <Stats stats={current.stats} />}
    </main>
  );
}

function Stats({ stats }: { stats: PlayerStats }) {
  const which = { all: 'a puzzle', daily: 'a daily', random: 'a random puzzle' }[stats.source];
  return (
    <>
      <dl className="grid grid-cols-3 gap-3">
        <Tile icon={<Flame className="size-4 text-warning" aria-hidden="true" />} label="Streak" value={`${stats.currentStreak}`} note={stats.currentStreak === 1 ? 'day' : 'days'} />
        <Tile icon={<Trophy className="size-4 text-warning" aria-hidden="true" />} label="Best" value={`${stats.bestStreak}`} note={stats.bestStreak === 1 ? 'day' : 'days'} />
        <Tile label="Dailies" value={`${stats.dailiesSolved}`} note="solved" />
      </dl>

      {stats.difficulties.length === 0 ? (
        <p className="text-base-content/60">
          You haven't solved {which} yet.{' '}
          <Link to="/" className="link link-primary">
            Play one
          </Link>
        </p>
      ) : (
        <ul className="grid gap-3" aria-label="By difficulty">
          {stats.difficulties.map((d) => (
            <DifficultyCard key={d.difficulty} stats={d} includeAssisted={stats.includeAssisted} />
          ))}
        </ul>
      )}
    </>
  );
}

function DifficultyCard({ stats, includeAssisted }: { stats: DifficultyStats; includeAssisted: boolean }) {
  const clean = cleanPercent(stats);
  const trend = trendLine(stats);
  const faster = stats.recentAverageMs !== null && stats.averageMs !== null && stats.recentAverageMs < stats.averageMs;
  const time = (ms: number | null) => (ms === null ? '–' : formatTime(ms));
  return (
    <li className="card border border-base-300 bg-base-100 shadow-sm" aria-labelledby={`stats-${stats.difficulty}`}>
      <div className="card-body gap-3 p-4 sm:p-5">
        <h2 id={`stats-${stats.difficulty}`} className="flex items-baseline justify-between gap-2">
          <span className="text-lg font-semibold capitalize">{stats.difficulty}</span>
          <span className="text-sm text-base-content/60">
            {stats.solved} {stats.solved === 1 ? 'solve' : 'solves'}
          </span>
        </h2>
        {stats.solved > 0 && (
          <dl className="grid grid-cols-3 gap-2 sm:grid-cols-5">
            <Stat label="Average" value={time(stats.averageMs)} />
            <Stat label="Median" value={time(stats.medianMs)} />
            <Stat label="Best" value={time(stats.bestMs)} />
            <Stat label="Clean" value={clean === null ? '–' : `${clean}%`} icon={<Target className="size-3.5 text-success" aria-hidden="true" />} />
            <Stat label="Mistakes" value={formatMistakes(stats.averageMistakes)} note="avg" />
          </dl>
        )}
        {trend && (
          <p className="flex items-center gap-1.5 text-sm text-base-content/70">
            {faster ? <TrendingDown className="size-4 text-success" aria-hidden="true" /> : <TrendingUp className="size-4 text-base-content/50" aria-hidden="true" />}
            {trend}
          </p>
        )}
        {!includeAssisted && stats.assisted > 0 && (
          <p className="flex items-center gap-1.5 text-xs text-base-content/60">
            <Lightbulb className="size-3.5" aria-hidden="true" />
            {stats.assisted} {stats.assisted === 1 ? 'solve' : 'solves'} with hints or auto notes not counted.
          </p>
        )}
      </div>
    </li>
  );
}

function Stat({ label, value, note, icon }: { label: string; value: string; note?: string; icon?: ReactNode }) {
  return (
    <div className="grid rounded-box bg-base-200 px-3 py-2">
      <dt className="flex items-center gap-1 text-xs font-semibold tracking-wide text-base-content/60 uppercase">
        {icon} {label}
      </dt>
      <dd className="text-lg font-bold tabular-nums">
        {value} {note && <span className="text-xs font-normal text-base-content/60">{note}</span>}
      </dd>
    </div>
  );
}
