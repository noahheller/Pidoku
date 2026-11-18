import { ArrowLeft, Trophy } from 'lucide-react';
import { Link, Navigate, useParams, useSearchParams } from 'react-router';
import { DAILY_DIFFICULTIES, type Difficulty } from '../api/client';
import { dailyLabel } from '../game/daily';
import { formatDate } from '../game/format';
import { LeaderboardView } from './Leaderboard';

/**
 * `/leaderboard/{date}?difficulty=hard`: one date's daily leaderboards, a tab per difficulty.
 * Opened from the home screen's list of today's puzzles.
 */
export function LeaderboardPage() {
  const { date = '' } = useParams();
  const [params, setParams] = useSearchParams();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return <Navigate to="/" replace />;
  const asked = params.get('difficulty');
  const difficulty: Difficulty = DAILY_DIFFICULTIES.find((d) => d === asked) ?? 'easy';

  return (
    <main className="mx-auto grid max-w-xl gap-5 px-4 pt-4 pb-16">
      <header className="grid gap-3">
        <Link to="/" className="btn btn-ghost btn-sm w-fit text-primary">
          <ArrowLeft className="size-4 shrink-0" aria-hidden="true" /> Puzzles
        </Link>
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold">
            <Trophy className="size-6 text-warning" aria-hidden="true" /> Leaderboards
          </h1>
          <p className="text-sm text-base-content/60">
            {dailyLabel(date)}{formatDate(date)}
          </p>
        </div>
      </header>

      <div role="tablist" aria-label="Difficulty" className="tabs tabs-border">
        {DAILY_DIFFICULTIES.map((d) => (
          <button
            key={d}
            type="button"
            role="tab"
            aria-selected={d === difficulty}
            className={`tab capitalize ${d === difficulty ? 'tab-active' : ''}`}
            onClick={() => setParams({ difficulty: d }, { replace: true })}
          >
            {d}
          </button>
        ))}
      </div>

      <section className="card border border-base-300 bg-base-100 shadow-sm">
        <div className="card-body p-4 sm:p-5">
          <LeaderboardView key={difficulty} date={date} difficulty={difficulty} />
        </div>
      </section>
    </main>
  );
}
