import { ArrowLeft, ChevronRight, CircleAlert, CloudOff, Flame, History as HistoryIcon, Lock, RotateCw, Swords, Target, Trophy, UserPlus } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { ApiError, DAILY_DIFFICULTIES, fetchHistory, fetchProfile, type HistoryEntry, type PlayerProfile } from '../api/client';
import { dailyLabel } from '../game/daily';
import { formatDate, formatPenalty, formatTime } from '../game/format';
import { challengeFrom, historyShownTo, versus, type HistoryOwner } from '../game/history';
import { formatGap } from '../game/leaderboardStats';
import type { PuzzleDef } from '../game/puzzleFile';
import { clearPlayerCaches } from '../game/storage';
import { reloadCosmetics } from './cosmetics';
import { GoogleButton } from './GoogleButton';
import { Switch } from './Leaderboard';
import type { PlayOptions } from './PuzzlePicker';
import { Failed, Loading } from './SubPage';
import { VISIBILITY } from './visibility';

/** Opens a puzzle the way the home screen does (it goes on Recent), with a time to beat if there is one. */
type OnPlay = (puzzle: PuzzleDef, options?: PlayOptions) => void;

type State =
  | { status: 'loading' }
  | { status: 'loaded'; profile: PlayerProfile }
  /** Someone else's profile, and the player isn't signed in with Google. */
  | { status: 'signInNeeded' }
  | { status: 'failed'; message: string };

/**
 * `/players/{playerId}` (and `/friends/{playerId}`, its older address): a player's streaks,
 * totals, best times, last week of dailies and puzzle history. The player's own, a friend's, or a
 * public one; the server says which it may show, and whether the history is shown too. Someone
 * else's needs a Google sign-in, which it offers. A puzzle in someone else's history opens with
 * their time to beat.
 */
export function PlayerPage({ onPlay }: { onPlay: OnPlay }) {
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
          <button type="button" className="btn btn-plain btn-sm" onClick={() => setRetries((n) => n + 1)}>
            <RotateCw className="size-4" aria-hidden="true" /> Retry
          </button>
        </div>
      )}
      {current.status === 'signInNeeded' && (
        <SignInNeeded
          onSignedIn={(result) => {
            // Another player now: what was cached for the old one no longer applies.
            if (result === 'switched') {
              clearPlayerCaches();
              reloadCosmetics();
            }
            setRetries((n) => n + 1);
          }}
        />
      )}
      {current.status === 'loaded' && <Profile profile={current.profile} onPlay={onPlay} />}
    </main>
  );
}

/** Back where the player came from (a leaderboard, their friends), or home when opened cold. */
function BackLink() {
  const navigate = useNavigate();
  const location = useLocation();
  const className = 'back-link btn btn-ghost btn-sm w-fit text-primary';
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
        <Link to="/me/account" className="link link-primary">
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

function Profile({ profile, onPlay }: { profile: PlayerProfile; onPlay: OnPlay }) {
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

      {profile.historyVisible !== undefined && <History profile={profile} onPlay={onPlay} />}
    </>
  );
}

type HistoryState =
  | { status: 'loading' }
  | { status: 'loaded'; entries: HistoryEntry[]; hasMore: boolean; more: 'idle' | 'loading' | 'failed' }
  | { status: 'failed'; message: string };

/**
 * Every puzzle the player has solved, newest first, a page at a time, if they show it to this
 * player. "In common" keeps the ones this player or their friends have solved too. Opening one of
 * someone else's puzzles puts their time up to beat.
 */
function History({ profile, onPlay }: { profile: PlayerProfile; onPlay: OnPlay }) {
  const [inCommon, setInCommon] = useState(false);
  const [retries, setRetries] = useState(0);
  const key = `${profile.id}/${inCommon}/${retries}`;
  const [state, setState] = useState<HistoryState & { key?: string }>({ status: 'loading' });
  const visible = profile.historyVisible === true;

  useEffect(() => {
    if (!visible) return;
    const controller = new AbortController();
    fetchHistory(profile.id, { inCommon }, controller.signal)
      .then((page) => setState({ status: 'loaded', entries: page.entries, hasMore: page.hasMore, more: 'idle', key }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ status: 'failed', message: e instanceof Error ? e.message : String(e), key });
      });
    return () => controller.abort();
  }, [visible, profile.id, inCommon, key]);

  const current: HistoryState = state.key === key ? state : { status: 'loading' };
  const name = profile.displayName ?? 'Anonymous';
  const owner: HistoryOwner = { displayName: profile.displayName, you: profile.you, friend: profile.friendsSince !== null };

  const loadMore = () => {
    if (current.status !== 'loaded' || current.more === 'loading') return;
    setState({ ...current, more: 'loading', key });
    const update = (change: (s: Extract<HistoryState, { status: 'loaded' }>) => HistoryState) =>
      setState((s) => (s.key === key && s.status === 'loaded' ? { ...change(s), key } : s));
    fetchHistory(profile.id, { offset: current.entries.length, inCommon })
      .then((page) => update((s) => ({ ...s, entries: [...s.entries, ...page.entries], hasMore: page.hasMore, more: 'idle' })))
      .catch(() => update((s) => ({ ...s, more: 'failed' })));
  };

  return (
    <section className="card border border-base-300 bg-base-100 shadow-sm" aria-labelledby="history-title">
      <div className="card-body gap-3 p-4 sm:p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="history-title" className="flex items-center gap-1.5 font-semibold">
            <HistoryIcon className="size-4 text-primary" aria-hidden="true" /> Puzzle history
          </h2>
          {visible && (
            <Switch
              label="Which puzzles"
              options={[
                ['all', 'All'],
                ['common', 'In common'],
              ]}
              value={inCommon ? 'common' : 'all'}
              onChange={(v) => setInCommon(v === 'common')}
            />
          )}
        </div>
        {!visible ? (
          <p className="text-sm text-base-content/60">
            {profile.historyVisibility === 'friends' ? `${name} only shows their puzzle history to friends.` : `${name} keeps their puzzle history to themselves.`}
          </p>
        ) : (
          <>
            <HistoryCaption profile={profile} inCommon={inCommon} />
            {current.status === 'loading' && <Loading />}
            {current.status === 'failed' && <Failed message={current.message} onRetry={() => setRetries((n) => n + 1)} />}
            {current.status === 'loaded' && current.entries.length === 0 && <EmptyHistory profile={profile} inCommon={inCommon} />}
            {current.status === 'loaded' && current.entries.length > 0 && (
              <ul className="grid gap-2" aria-label={profile.you ? 'Puzzles you solved' : `Puzzles ${name} solved`}>
                {current.entries.map((entry) => (
                  <li key={entry.puzzle.id}>
                    <HistoryRow entry={entry} owner={owner} onPlay={onPlay} />
                  </li>
                ))}
              </ul>
            )}
            {current.status === 'loaded' && current.hasMore && (
              <button type="button" className="btn btn-plain btn-sm w-fit" onClick={loadMore} disabled={current.more === 'loading'}>
                {current.more === 'loading' && <span className="loading loading-spinner loading-xs" aria-hidden="true" />}
                {current.more === 'failed' ? "Couldn't load more. Try again" : 'Show more'}
              </button>
            )}
          </>
        )}
      </div>
    </section>
  );
}

/** What the tab shows, and on the player's own profile who else can see it. */
function HistoryCaption({ profile, inCommon }: { profile: PlayerProfile; inCommon: boolean }) {
  if (inCommon) {
    return <p className="text-sm text-base-content/60">{profile.you ? 'Puzzles a friend has solved too.' : 'Puzzles you or your friends have solved too.'}</p>;
  }
  if (!profile.you) return null;
  const { label, Icon } = VISIBILITY[historyShownTo(profile.visibility, profile.historyVisibility ?? 'friends')];
  return (
    <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-base-content/60">
      <span className="flex items-center gap-1">
        <Icon className="size-4" aria-hidden="true" /> Seen by: {label}
      </span>
      <Link to="/me/account" className="link link-primary">
        Change who can see it
      </Link>
    </p>
  );
}

function EmptyHistory({ profile, inCommon }: { profile: PlayerProfile; inCommon: boolean }) {
  const name = profile.displayName ?? 'Anonymous';
  if (!inCommon) {
    return <p className="text-sm text-base-content/60">{profile.you ? "You haven't solved a puzzle yet." : `${name} hasn't solved a puzzle yet.`}</p>;
  }
  return (
    <p className="text-sm text-base-content/60">
      {profile.you ? "None of your friends has solved one of your puzzles yet." : `You and your friends haven't solved any of ${name}'s puzzles yet.`}{' '}
      <Link to="/friends" className="link link-primary inline-flex items-center gap-1">
        <UserPlus className="size-4" aria-hidden="true" /> Add friends
      </Link>
    </p>
  );
}

/**
 * One solved puzzle: what it is, the owner's time, and the player's and their friends' times on
 * it. The whole row opens the puzzle.
 */
function HistoryRow({ entry, owner, onPlay }: { entry: HistoryEntry; owner: HistoryOwner; onPlay: OnPlay }) {
  const { puzzle, solve, others } = entry;
  const challenge = challengeFrom(entry, owner);
  const vs = owner.you ? null : versus(entry);
  const date = puzzle.dailyDate;
  const solvedOn = new Date(solve.completedAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });

  return (
    <button
      type="button"
      className="grid w-full cursor-pointer gap-1.5 rounded-box border border-base-300 p-3 text-left transition hover:border-primary focus-visible:outline-2 focus-visible:outline-primary"
      onClick={() => onPlay(puzzle, { timeToBeat: challenge })}
    >
      <span className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <span className="grid">
          <span className="font-semibold">
            {puzzle.difficulty} <span className="font-normal text-base-content/60">· {date ? `${dailyLabel(date) || 'Daily · '}${formatDate(date)}` : 'Random'}</span>
          </span>
          <span className="text-xs text-base-content/60">Solved {solvedOn}</span>
        </span>{' '}
        <span className="flex items-center gap-2 text-sm">
          <span className="font-semibold tabular-nums">
            {formatTime(solve.elapsedMs)}
            {solve.penaltyMs > 0 && <span className="ml-1 text-xs font-normal text-warning">{formatPenalty(solve.penaltyMs)}</span>}
          </span>{' '}
          <span className={solve.mistakes === 0 ? 'text-success' : 'text-base-content/60'}>{solve.mistakes === 0 ? '✓' : `${solve.mistakes}✗`}</span>
          {solve.late && <CloudOff className="size-3.5 text-base-content/50" aria-label="Sent late" />}
        </span>
      </span>
      {others.length > 0 && (
        <span className="flex flex-wrap gap-1.5">
          {others.map((o) => (
            <span key={o.playerId} className={`flex items-center gap-1 rounded-field px-2 py-0.5 text-xs ${o.you ? 'bg-primary/10 font-semibold' : 'bg-base-200'}`}>
              <span className={o.displayName || o.you ? '' : 'italic'}>{o.you ? 'You' : (o.displayName ?? 'Anonymous')}</span>{' '}
              <span className="tabular-nums">
                {formatTime(o.elapsedMs)}
                {o.penaltyMs > 0 && <span className="text-warning"> {formatPenalty(o.penaltyMs)}</span>}
              </span>
              {o.you && vs && ' '}
              {o.you && vs && (
                <span className={vs.outcome === 'faster' ? 'text-success' : vs.outcome === 'slower' ? 'text-base-content/60' : ''}>
                  · {vs.outcome === 'tied' ? 'tied' : `${formatGap(vs.byMs)} ${vs.outcome}`}
                </span>
              )}
            </span>
          ))}
        </span>
      )}{' '}
      <span className="flex items-center gap-1 text-xs font-semibold text-primary">
        {challenge ? (
          <>
            <Swords className="size-3.5" aria-hidden="true" /> Try to beat {owner.displayName ?? 'their time'}
          </>
        ) : (
          <>
            Open puzzle <ChevronRight className="size-3.5" aria-hidden="true" />
          </>
        )}
      </span>
    </button>
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
