import { ArrowLeft, ChevronLeft, ChevronRight, Trophy } from 'lucide-react';
import { useState } from 'react';
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router';
import { DAILY_DIFFICULTIES, localDate, type Difficulty } from '../api/client';
import { addDays, clampDate, dailyLabel, LAUNCH_DATE } from '../game/daily';
import { formatDate } from '../game/format';
import { LeaderboardView } from './Leaderboard';

/**
 * `/leaderboard/{date}?difficulty=hard`: one date's daily leaderboards, a tab per difficulty.
 * Opened from the home screen's list of today's puzzles; a calendar (and previous/next day buttons)
 * moves to any date from launch day to today.
 */
export function LeaderboardPage() {
  const { date = '' } = useParams();
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  // The player's own date, like the dailies: a later one has no leaderboard yet. Read once.
  const [today] = useState(() => localDate(new Date()));
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return <Navigate to="/" replace />;
  const asked = params.get('difficulty');
  const difficulty: Difficulty = DAILY_DIFFICULTIES.find((d) => d === asked) ?? 'easy';

  // Replace rather than push, like the difficulty tabs, so Back still leads out of the leaderboards.
  const goTo = (day: string) => {
    const target = clampDate(day, LAUNCH_DATE, today);
    if (target !== date) void navigate(`/leaderboard/${target}?difficulty=${difficulty}`, { replace: true });
  };

  return (
    <main className="mx-auto grid max-w-xl gap-5 px-4 pt-4 pb-16">
      <header className="grid gap-3">
        <Link to="/" className="back-link btn btn-ghost btn-sm w-fit text-primary">
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

        <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Date">
          <button
            type="button"
            className="btn btn-square btn-sm border-base-300 bg-base-100"
            onClick={() => goTo(addDays(date, -1))}
            disabled={date <= LAUNCH_DATE}
            aria-label="Previous day"
            title="Previous day"
          >
            <ChevronLeft className="size-4" aria-hidden="true" />
          </button>
          {/* The browser's own date picker: a calendar on desktop, the system picker on phones.
              No icon of ours: browsers that have one draw their own calendar button. */}
          <label className="input input-sm w-auto">
            <span className="sr-only">Show the leaderboards for</span>
            <input
              type="date"
              className="text-base sm:text-sm"
              value={date}
              min={LAUNCH_DATE}
              max={today}
              // A date typed outside the range is pulled back into it; a cleared field is ignored.
              onChange={(e) => e.target.value && goTo(e.target.value)}
            />
          </label>
          <button
            type="button"
            className="btn btn-square btn-sm border-base-300 bg-base-100"
            onClick={() => goTo(addDays(date, 1))}
            disabled={date >= today}
            aria-label="Next day"
            title="Next day"
          >
            <ChevronRight className="size-4" aria-hidden="true" />
          </button>
          {date !== today && (
            <button type="button" className="btn btn-ghost btn-sm text-primary" onClick={() => goTo(today)}>
              Today
            </button>
          )}
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
          <LeaderboardView key={`${date}/${difficulty}`} date={date} difficulty={difficulty} />
        </div>
      </section>
    </main>
  );
}
