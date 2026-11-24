import { ChevronRight, CircleAlert, CloudOff, RotateCw, Target, UserPlus } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { fetchLeaderboard, type Difficulty, type Leaderboard, type LeaderboardEntry } from '../api/client';
import { formatTime } from '../game/format';
import { gapLine, photoFinishes, standing } from '../game/leaderboardStats';
import {
  loadLeaderboardRanking,
  loadLeaderboardScope,
  saveLeaderboardRanking,
  saveLeaderboardScope,
  type LeaderboardRanking,
  type LeaderboardScope,
} from '../game/storage';

type State = { status: 'loading' } | { status: 'loaded'; board: Leaderboard } | { status: 'failed'; message: string };

interface LeaderboardViewProps {
  /** The daily's date, YYYY-MM-DD. */
  date: string;
  difficulty: Difficulty;
  title?: string;
}

/**
 * One daily's leaderboard. Two switches, both remembered: whose times (everyone or friends), and
 * how they're ranked (fastest overall, mistakes or not, or clean: only solves with no mistakes).
 * Each player's first solve counts, timed by the game's clock (which pauses while they're away).
 */
export function LeaderboardView({ date, difficulty, title }: LeaderboardViewProps) {
  const [scope, setScope] = useState<LeaderboardScope>(loadLeaderboardScope);
  const [ranking, setRanking] = useState<LeaderboardRanking>(loadLeaderboardRanking);
  const [state, setState] = useState<State & { key?: string }>({ status: 'loading' });
  const [retries, setRetries] = useState(0);
  const key = `${date}/${difficulty}/${scope}/${ranking}/${retries}`;

  useEffect(() => {
    const controller = new AbortController();
    fetchLeaderboard(date, difficulty, { friendsOnly: scope === 'friends', cleanOnly: ranking === 'clean' }, controller.signal)
      .then((board) => setState({ status: 'loaded', board, key }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ status: 'failed', message: e instanceof Error ? e.message : String(e), key });
      });
    return () => controller.abort();
  }, [date, difficulty, scope, ranking, key]);

  const current: State = state.key === key ? state : { status: 'loading' };

  return (
    <div className="grid gap-3">
      {title && <h3 className="font-semibold">{title}</h3>}
      <div className="flex flex-wrap items-center gap-2">
        <Switch
          label="Whose times"
          options={[
            ['everyone', 'Everyone'],
            ['friends', 'Friends'],
          ]}
          value={scope}
          onChange={(s) => {
            setScope(s);
            saveLeaderboardScope(s);
          }}
        />
        <Switch
          label="Ranking"
          options={[
            ['fastest', 'Fastest'],
            ['clean', 'Clean'],
          ]}
          value={ranking}
          onChange={(r) => {
            setRanking(r);
            saveLeaderboardRanking(r);
          }}
        />
      </div>
      <p className="text-xs text-base-content/60">
        {ranking === 'clean' ? 'Only solves with no mistakes, fastest first.' : 'Fastest time, whatever the mistakes.'}
      </p>

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

function Switch<T extends string>({ label, options, value, onChange }: { label: string; options: [T, string][]; value: T; onChange: (value: T) => void }) {
  return (
    <div role="tablist" aria-label={label} className="tabs tabs-box tabs-sm">
      {options.map(([option, text]) => (
        <button key={option} type="button" role="tab" aria-selected={value === option} className={`tab ${value === option ? 'tab-active' : ''}`} onClick={() => onChange(option)}>
          {text}
        </button>
      ))}
    </div>
  );
}

function Board({ board }: { board: Leaderboard }) {
  if (board.total === 0) {
    const what = board.cleanOnly ? ' without a mistake' : '';
    return board.friendsOnly ? (
      <p className="text-sm text-base-content/60">
        None of your friends has solved this one{what} yet.{' '}
        <Link to="/me" className="link link-primary inline-flex items-center gap-1">
          <UserPlus className="size-4" aria-hidden="true" /> Add friends
        </Link>
      </p>
    ) : (
      <p className="text-sm text-base-content/60">No one has solved this one{what} yet. Be the first!</p>
    );
  }
  const youBeyondTop = board.you !== null && !board.entries.some((e) => e.you);
  const close = photoFinishes(board.entries);
  const where = standing(board);
  const gap = gapLine(board);

  return (
    <div className="grid gap-2">
      {(where || gap) && (
        <div className="rounded-box bg-primary/10 px-3 py-2 text-sm">
          {where && <p className="font-semibold">{where}</p>}
          {gap && <p className="text-base-content/70">{gap}</p>}
        </div>
      )}
      <ol className="grid gap-1" aria-label={`${board.friendsOnly ? 'Friends' : 'Everyone'}, ${board.cleanOnly ? 'clean solves' : 'fastest'} first`}>
        {board.entries.map((entry, i) => (
          <Row key={i} entry={entry} photoFinish={close.has(i)} showClean={!board.cleanOnly} />
        ))}
      </ol>
      {youBeyondTop && (
        <>
          <p className="text-center text-base-content/40" aria-hidden="true">
            ⋮
          </p>
          <ol aria-label="Your place">
            <Row entry={board.you!} photoFinish={false} showClean={!board.cleanOnly} />
          </ol>
        </>
      )}
      <Highlights board={board} />
      <p className="text-xs text-base-content/60">
        {board.total} {board.total === 1 ? 'player has' : 'players have'} solved it{board.cleanOnly ? ' without a mistake' : ''}. Each player's first solve counts. Times are time spent on the puzzle: the clock pauses while a player is away.
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

/** The fastest clean solve: a line under the list, with what its badge means. */
function Highlights({ board }: { board: Leaderboard }) {
  const name = (e: LeaderboardEntry) => (e.you ? 'You' : (e.displayName ?? 'Anonymous'));
  const items: [string, ReactNode][] = [];
  // On the clean board the fastest clean solve is simply first place.
  if (board.fastestClean && !board.cleanOnly) {
    items.push(['clean', <><CleanIcon /> Fastest clean: {name(board.fastestClean)} {formatTime(board.fastestClean.elapsedMs)}</>]);
  }
  if (items.length === 0) return null;
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-base-content/70" aria-label="Highlights">
      {items.map(([key, item]) => (
        <li key={key} className="flex items-center gap-1">
          {item}
        </li>
      ))}
    </ul>
  );
}

function CleanIcon() {
  return <Target className="inline size-3.5 shrink-0 text-success" aria-hidden="true" />;
}

function Row({ entry, photoFinish, showClean }: { entry: LeaderboardEntry; photoFinish: boolean; showClean: boolean }) {
  const medal = entry.rank <= 3 ? ['bg-warning/25', 'bg-base-300', 'bg-orange-300/30'][entry.rank - 1] : 'bg-transparent';
  return (
    <li
      className={`relative flex items-center gap-2 rounded-field px-2 py-1.5 sm:gap-3 ${entry.you ? 'bg-primary/10 ring-1 ring-primary/40' : ''} ${entry.profileId !== null ? 'transition hover:bg-base-200' : ''}`}
    >
      <span className={`grid size-7 shrink-0 place-items-center rounded-full text-sm font-bold tabular-nums ${medal}`}>{entry.rank}</span>
      <span className="min-w-0 flex-1 truncate">
        {entry.profileId !== null ? (
          <Link
            to={`/players/${entry.profileId}`}
            className="after:absolute after:inset-0 after:rounded-field after:content-[''] focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:outline-primary"
            aria-label={`${entry.you ? 'Your' : `${entry.displayName ?? 'Anonymous'}'s`} profile`}
          >
            <Name entry={entry} />
          </Link>
        ) : (
          <Name entry={entry} />
        )}
        {entry.you && <span className="ml-2 badge badge-primary badge-xs align-middle">you</span>}
        {showClean && entry.fastestClean && (
          <span className="ml-1.5 inline-flex align-middle" title="Fastest solve with no mistakes">
            <CleanIcon />
            <span className="sr-only">Fastest clean solve</span>
          </span>
        )}
        {photoFinish && <span className="ml-2 badge badge-ghost badge-xs align-middle whitespace-nowrap">photo finish</span>}
        {entry.you && entry.displayName === null && (
          <Link to="/me" className="link link-primary relative z-10 ml-2 text-xs">
            Pick a name
          </Link>
        )}
      </span>
      {entry.late && (
        <span className="relative z-10 text-base-content/50" title="Sent late (offline at the time): the time is partly what their device reported">
          <CloudOff className="size-4" aria-hidden="true" />
          <span className="sr-only">Sent late</span>
        </span>
      )}
      <span
        className={`relative z-10 w-8 text-right text-sm tabular-nums ${entry.mistakes === 0 ? 'text-success' : 'text-base-content/60'}`}
        title={entry.mistakes === 0 ? 'No mistakes' : `${entry.mistakes} mistake${entry.mistakes === 1 ? '' : 's'}`}
      >
        {entry.mistakes === 0 ? '✓' : `${entry.mistakes}✗`}
      </span>
      <span className="w-14 text-right font-semibold tabular-nums">{formatTime(entry.elapsedMs)}</span>
      {/* Same width either way, so the times stay in one column. */}
      <ChevronRight className={`size-4 shrink-0 ${entry.profileId !== null ? 'text-base-content/40' : 'invisible'}`} aria-hidden="true" />
    </li>
  );
}

function Name({ entry }: { entry: LeaderboardEntry }) {
  return entry.displayName ?? <span className="text-base-content/60 italic">Anonymous</span>;
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
