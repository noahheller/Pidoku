import { CircleAlert, CloudOff, RotateCw, UserPlus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { fetchLeaderboard, type Difficulty, type Leaderboard, type LeaderboardEntry } from '../api/client';
import { formatTime } from '../game/format';
import { loadLeaderboardScope, saveLeaderboardScope, type LeaderboardScope } from '../game/storage';

type State = { status: 'loading' } | { status: 'loaded'; board: Leaderboard } | { status: 'failed'; message: string };

interface LeaderboardViewProps {
  /** The daily's date, YYYY-MM-DD. */
  date: string;
  difficulty: Difficulty;
  /** A heading level to fit where it's shown. */
  title?: string;
}

/**
 * One daily's fastest solves, with tabs for everyone and friends only. The tab picked last is
 * remembered. Each player's first solve counts, timed by the server.
 */
export function LeaderboardView({ date, difficulty, title }: LeaderboardViewProps) {
  const [scope, setScope] = useState<LeaderboardScope>(loadLeaderboardScope);
  const [state, setState] = useState<State & { key?: string }>({ status: 'loading' });
  const [retries, setRetries] = useState(0);
  const key = `${date}/${difficulty}/${scope}/${retries}`;

  useEffect(() => {
    const controller = new AbortController();
    fetchLeaderboard(date, difficulty, scope === 'friends', controller.signal)
      .then((board) => setState({ status: 'loaded', board, key }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ status: 'failed', message: e instanceof Error ? e.message : String(e), key });
      });
    return () => controller.abort();
  }, [date, difficulty, scope, key]);

  const current: State = state.key === key ? state : { status: 'loading' };
  const pick = (next: LeaderboardScope) => {
    setScope(next);
    saveLeaderboardScope(next);
  };

  return (
    <div className="grid gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        {title && <h3 className="font-semibold">{title}</h3>}
        <div role="tablist" aria-label="Whose times" className="tabs tabs-box tabs-sm">
          {(['everyone', 'friends'] as const).map((s) => (
            <button key={s} type="button" role="tab" aria-selected={scope === s} className={`tab capitalize ${scope === s ? 'tab-active' : ''}`} onClick={() => pick(s)}>
              {s}
            </button>
          ))}
        </div>
      </div>

      {current.status === 'loading' && <Skeleton />}
      {current.status === 'failed' && (
        <div role="alert" className="alert alert-soft alert-warning flex flex-wrap">
          <CircleAlert className="size-5" aria-hidden="true" />
          <span>{current.message}</span>
          <button type="button" className="btn btn-sm" onClick={() => setRetries((n) => n + 1)}>
            <RotateCw className="size-4" aria-hidden="true" /> Retry
          </button>
        </div>
      )}
      {current.status === 'loaded' && <Board board={current.board} />}
    </div>
  );
}

function Board({ board }: { board: Leaderboard }) {
  if (board.total === 0) {
    return board.friendsOnly ? (
      <p className="text-sm text-base-content/60">
        None of your friends has solved this one yet.{' '}
        <Link to="/me" className="link link-primary inline-flex items-center gap-1">
          <UserPlus className="size-4" aria-hidden="true" /> Add friends
        </Link>
      </p>
    ) : (
      <p className="text-sm text-base-content/60">No one has solved this one yet. Be the first!</p>
    );
  }
  const youBeyondTop = board.you !== null && !board.entries.some((e) => e.you);
  return (
    <div className="grid gap-1">
      <ol className="grid gap-1" aria-label={`${board.friendsOnly ? 'Friends' : 'Everyone'}, fastest first`}>
        {board.entries.map((entry, i) => (
          <Row key={i} entry={entry} />
        ))}
      </ol>
      {youBeyondTop && (
        <>
          <p className="text-center text-base-content/40" aria-hidden="true">
            ⋮
          </p>
          <ol aria-label="Your place">
            <Row entry={board.you!} />
          </ol>
        </>
      )}
      <p className="mt-1 text-xs text-base-content/60">
        {board.total} {board.total === 1 ? 'player has' : 'players have'} solved it. Each player's first solve counts, timed by the server.
      </p>
      {board.friendsOnly && board.total === 1 && board.you && (
        <p className="text-sm text-base-content/60">
          <Link to="/me" className="link link-primary">
            Add friends
          </Link>{' '}
          to see how you compare.
        </p>
      )}
    </div>
  );
}

function Row({ entry }: { entry: LeaderboardEntry }) {
  const medal = entry.rank <= 3 ? ['bg-warning/25', 'bg-base-300', 'bg-orange-300/30'][entry.rank - 1] : 'bg-transparent';
  return (
    <li className={`flex items-center gap-3 rounded-field px-2 py-1.5 ${entry.you ? 'bg-primary/10 ring-1 ring-primary/40' : ''}`}>
      <span className={`grid size-7 shrink-0 place-items-center rounded-full text-sm font-bold tabular-nums ${medal}`}>{entry.rank}</span>
      <span className="min-w-0 flex-1 truncate">
        {entry.displayName ?? <span className="text-base-content/60 italic">Anonymous</span>}
        {entry.you && <span className="ml-2 badge badge-primary badge-xs align-middle">you</span>}
        {entry.you && entry.displayName === null && (
          <Link to="/me" className="link link-primary ml-2 text-xs">
            Pick a name
          </Link>
        )}
      </span>
      {entry.late && (
        <span className="text-base-content/50" title="Sent late (offline at the time): the time is partly what their device reported">
          <CloudOff className="size-4" aria-hidden="true" />
          <span className="sr-only">Sent late</span>
        </span>
      )}
      <span className={`w-8 text-right text-sm tabular-nums ${entry.mistakes === 0 ? 'text-success' : 'text-base-content/60'}`} title={entry.mistakes === 0 ? 'No mistakes' : `${entry.mistakes} mistake${entry.mistakes === 1 ? '' : 's'}`}>
        {entry.mistakes === 0 ? '✓' : `${entry.mistakes}✗`}
      </span>
      <span className="w-14 text-right font-semibold tabular-nums">{formatTime(entry.elapsedMs)}</span>
    </li>
  );
}

function Skeleton() {
  return (
    <div className="grid gap-2" aria-busy="true" aria-label="Loading leaderboard">
      {[0, 1, 2].map((i) => (
        <div key={i} className="flex items-center gap-3 px-2 py-1.5">
          <span className="skeleton size-7 rounded-full" />
          <span className="skeleton h-4 flex-1" />
          <span className="skeleton h-4 w-14" />
        </div>
      ))}
    </div>
  );
}
