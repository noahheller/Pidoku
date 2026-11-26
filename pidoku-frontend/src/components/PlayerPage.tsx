import { ArrowLeft, CircleAlert, CloudOff, Flame, Lock, RotateCw, Target, Trophy } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { ApiError, DAILY_DIFFICULTIES, fetchProfile, type PlayerProfile } from '../api/client';
import { dailyLabel } from '../game/daily';
import { formatDate, formatPenalty, formatTime } from '../game/format';
import { clearPlayerCaches } from '../game/storage';
import { GoogleButton } from './Profile';
import { VISIBILITY } from './visibility';

type State =
  | { status: 'loading' }
  | { status: 'loaded'; profile: PlayerProfile }
  /** Someone else's profile, and the player isn't signed in with Google. */
  | { status: 'signInNeeded' }
  | { status: 'failed'; message: string };

/**
 * `/players/{playerId}` (and `/friends/{playerId}`, its older address): a player's streaks,
 * totals, best times and last week of dailies. The player's own, a friend's, or a public one; the
 * server says which it may show. Someone else's needs a Google sign-in, which it offers.
 */
export function PlayerPage() {
  const { id = '' } = useParams();
  const playerId = Number(id);
  const [state, setState] = useState<State & { key?: string }>({ status: 'loading' });
  const [retries, setRetries] = useState(0);
  const key = `${id}/${retries}`;

  useEffect(() => {
    const controller = new AbortController();
    fetchProfile(playerId, controller.signal)
      .then((profile) => setState({ status: 'loaded', profile, key }))
      .catch((e: unknown) => {
        if (controller.signal.aborted) return;
        if (e instanceof ApiError && e.status === 403) setState({ status: 'signInNeeded', key });
        else setState({ status: 'failed', message: e instanceof Error ? e.message : String(e), key });
      });
    return () => controller.abort();
  }, [playerId, key]);

  const current: State = state.key === key ? state : { status: 'loading' };

  return (
    <main className="mx-auto grid max-w-xl gap-5 px-4 pt-4 pb-16">
      <BackLink />
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
      {current.status === 'signInNeeded' && (
        <SignInNeeded
          onSignedIn={(result) => {
            // Another player now: what was cached for the old one no longer applies.
            if (result === 'switched') clearPlayerCaches();
            setRetries((n) => n + 1);
          }}
        />
      )}
      {current.status === 'loaded' && <Profile profile={current.profile} />}
    </main>
  );
}

/** Back where the player came from (a leaderboard, their friends), or home when opened cold. */
function BackLink() {
  const navigate = useNavigate();
  const location = useLocation();
  const className = 'btn btn-ghost btn-sm w-fit text-primary';
  const content = (
    <>
      <ArrowLeft className="size-4 shrink-0" aria-hidden="true" /> Back
    </>
  );
  // 'default' is the key of the first page loaded in this tab: there's nothing in the app to go back to.
  if (location.key === 'default') {
    return (
      <Link to="/" className={className}>
        {content}
      </Link>
    );
  }
  return (
    <button type="button" className={className} onClick={() => void navigate(-1)}>
      {content}
    </button>
  );
}

function SignInNeeded({ onSignedIn }: { onSignedIn: (result: 'linked' | 'switched') => void }) {
  return (
    <section className="card border border-base-300 bg-base-100 shadow-sm">
      <div className="card-body items-center gap-3 p-6 text-center">
        <Lock className="size-8 text-primary" aria-hidden="true" />
        <h1 className="text-xl font-bold">Sign in to see profiles</h1>
        <p className="text-sm text-base-content/70">
          Other players' profiles are for players signed in with Google. Signing in also keeps your streak and times safe, and lets you play on any device.
        </p>
        <GoogleButton className="btn btn-primary" onDone={onSignedIn} />
      </div>
    </section>
  );
}

function Subtitle({ profile }: { profile: PlayerProfile }) {
  if (profile.you) {
    const { label, Icon } = VISIBILITY[profile.visibility];
    return (
      <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-base-content/60">
        <span className="flex items-center gap-1">
          <Icon className="size-4" aria-hidden="true" /> Your profile · {label}
        </span>
        <Link to="/me" className="link link-primary">
          Change who can see it
        </Link>
      </p>
    );
  }
  if (profile.friendsSince) {
    const since = new Date(profile.friendsSince).toLocaleDateString(undefined, { month: 'long', day: 'numeric', year: 'numeric' });
    return <p className="text-sm text-base-content/60">Friends since {since}</p>;
  }
  return <p className="text-sm text-base-content/60">Public profile</p>;
}

function Profile({ profile }: { profile: PlayerProfile }) {
  const name = profile.displayName ?? 'Anonymous';
  const cleanPercent = profile.dailiesSolved > 0 ? Math.round((profile.cleanSolves / profile.dailiesSolved) * 100) : null;
  const bests = DAILY_DIFFICULTIES.filter((d) => profile.bestTimes[d] !== undefined);
  const byDate = groupByDate(profile.recent);

  return (
    <>
      <header className="grid gap-1">
        <h1 className={`text-3xl font-bold ${profile.displayName ? '' : 'italic'}`}>{name}</h1>
        <Subtitle profile={profile} />
      </header>

      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Tile icon={<Flame className="size-4 text-warning" aria-hidden="true" />} label="Streak" value={`${profile.currentStreak}`} note={profile.currentStreak === 1 ? 'day' : 'days'} />
        <Tile icon={<Trophy className="size-4 text-warning" aria-hidden="true" />} label="Best streak" value={`${profile.bestStreak}`} note={profile.bestStreak === 1 ? 'day' : 'days'} />
        <Tile label="Dailies solved" value={`${profile.dailiesSolved}`} />
        <Tile icon={<Target className="size-4 text-success" aria-hidden="true" />} label="Clean" value={cleanPercent === null ? '–' : `${cleanPercent}%`} note="no mistakes or hints" />
      </dl>

      {bests.length > 0 && (
        <section className="card border border-base-300 bg-base-100 shadow-sm" aria-labelledby="bests-title">
          <div className="card-body gap-3 p-4 sm:p-5">
            <h2 id="bests-title" className="font-semibold">
              Best times
            </h2>
            <ul className="grid grid-cols-3 gap-2">
              {bests.map((d) => (
                <li key={d} className="grid rounded-box bg-base-200 px-3 py-2">
                  <span className="text-xs font-semibold tracking-wide text-base-content/60 uppercase">{d}</span>
                  <span className="text-xl font-bold tabular-nums">{formatTime(profile.bestTimes[d]!)}</span>
                </li>
              ))}
            </ul>
          </div>
        </section>
      )}

      <section className="card border border-base-300 bg-base-100 shadow-sm" aria-labelledby="recent-title">
        <div className="card-body gap-3 p-4 sm:p-5">
          <h2 id="recent-title" className="font-semibold">
            This week
          </h2>
          {byDate.length === 0 ? (
            <p className="text-sm text-base-content/60">{profile.you ? "You haven't" : `${name} hasn't`} solved a daily this week.</p>
          ) : (
            <ul className="grid gap-3">
              {byDate.map(([date, solves]) => (
                <li key={date} className="grid gap-1">
                  <Link to={`/leaderboard/${date}?difficulty=${solves[0].difficulty}`} className="link link-hover text-sm text-base-content/60">
                    {dailyLabel(date)}{formatDate(date)}
                  </Link>
                  <ul className="flex flex-wrap gap-2">
                    {solves.map((s) => (
                      <li key={s.difficulty} className="flex items-center gap-2 rounded-field bg-base-200 px-2.5 py-1 text-sm">
                        <span className="capitalize">{s.difficulty}</span>
                        <span className="font-semibold tabular-nums">
                          {formatTime(s.elapsedMs)}
                          {(s.penaltyMs ?? 0) > 0 && <span className="ml-1 text-xs font-normal text-warning">{formatPenalty(s.penaltyMs)}</span>}
                        </span>
                        <span className={s.mistakes === 0 ? 'text-success' : 'text-base-content/60'}>{s.mistakes === 0 ? '✓' : `${s.mistakes}✗`}</span>
                        {s.late && <CloudOff className="size-3.5 text-base-content/50" aria-label="Sent late" />}
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </>
  );
}

/** One number in a row of stat tiles, inside a `<dl>`. */
export function Tile({ icon, label, value, note }: { icon?: ReactNode; label: string; value: string; note?: string }) {
  return (
    <div className="grid gap-0.5 rounded-box border border-base-300 bg-base-100 p-3">
      <dt className="flex items-center gap-1 text-xs font-semibold tracking-wide text-base-content/60 uppercase">
        {icon} {label}
      </dt>
      <dd className="text-2xl font-bold tabular-nums">
        {value} {note && <span className="text-sm font-normal text-base-content/60">{note}</span>}
      </dd>
    </div>
  );
}

function groupByDate(recent: PlayerProfile['recent']): [string, PlayerProfile['recent']][] {
  const groups = new Map<string, PlayerProfile['recent']>();
  for (const solve of recent) groups.set(solve.date, [...(groups.get(solve.date) ?? []), solve]);
  return [...groups];
}
