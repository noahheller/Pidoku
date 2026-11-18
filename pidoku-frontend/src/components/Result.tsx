import { Check, CircleX, CloudOff, Copy, Flame, ShieldCheck, Timer, Trophy } from 'lucide-react';
import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import { currentAccount } from '../api/auth';
import { ApiError, DAILY_DIFFICULTIES, fetchPlayer, fetchStreak, localDate, updateDisplayName, type Streak } from '../api/client';
import { dailyNumber, shareLine, shareUrl } from '../game/daily';
import { formatTime } from '../game/format';
import type { PuzzleDef } from '../game/puzzleFile';
import { clearPlayerCaches, dismissGooglePrompt, dismissNamePrompt, isGooglePromptDismissed, isNamePromptDismissed, saveStreak } from '../game/storage';
import { LeaderboardView } from './Leaderboard';
import { GoogleButton } from './Profile';

/** A streak this long is worth keeping: past it, players without an account are asked to sign in with Google. */
const STREAK_WORTH_KEEPING = 3;

/** What the server knows about this solve. */
export type Recorded =
  /** Not a puzzle from the API, or its attempt never started (offline, signed out). */
  | { status: 'none' }
  | { status: 'saving' }
  /** The API has it: `elapsedMs` is the server's time. */
  | { status: 'recorded'; elapsedMs: number; late: boolean }
  /** Couldn't be sent yet; it's kept and sent when the connection comes back. */
  | { status: 'unsent' };

interface ResultProps {
  puzzle: PuzzleDef;
  /** The game's own timer, shown when the server's time isn't known. */
  elapsedMs: number;
  mistakes: number;
  recorded: Recorded;
  onExit: () => void;
}

/**
 * Shown above the board once the puzzle is solved: the time (the server's, when it has one),
 * mistakes, for a daily the streak and leaderboard, and a line to paste to friends. The first time
 * a solve is recorded, it also asks for the name friends will see; once there's a streak worth
 * keeping, it suggests signing in with Google.
 */
export function Result({ puzzle, elapsedMs, mistakes, recorded, onExit }: ResultProps) {
  const isRecorded = recorded.status === 'recorded';
  const streak = useStreak(isRecorded && puzzle.dailyDate !== undefined);
  const dailyDifficulty = DAILY_DIFFICULTIES.find((d) => d === puzzle.difficulty?.toLowerCase());
  const time = isRecorded ? recorded.elapsedMs : elapsedMs;
  const share = shareLine({
    difficulty: puzzle.difficulty,
    dailyDate: puzzle.dailyDate ?? null,
    elapsedMs: time,
    mistakes: puzzle.solution ? mistakes : null,
    url: puzzle.shareCode ? shareUrl(puzzle.shareCode) : null,
  });

  return (
    <section className="card border-2 border-success bg-base-100 shadow-sm" aria-labelledby="result-title">
      <div className="card-body gap-4 p-4 sm:p-5">
        <header className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <h2 id="result-title" className="flex items-center gap-1.5 text-xl font-bold">
            <Trophy className="size-5 text-success" aria-hidden="true" /> Solved!
          </h2>
          {puzzle.dailyDate && (
            <span className="text-sm text-base-content/60">
              Pidoku #{dailyNumber(puzzle.dailyDate)} · {puzzle.difficulty}
            </span>
          )}
        </header>

        <dl className="flex flex-wrap gap-x-8 gap-y-3">
          <Stat icon={<Timer className="size-4" aria-hidden="true" />} label="Time" value={formatTime(time)} note={timeNote(recorded)} />
          {puzzle.solution && <Stat icon={<CircleX className="size-4" aria-hidden="true" />} label="Mistakes" value={String(mistakes)} />}
          {puzzle.dailyDate && streak && <StreakStat streak={streak} />}
        </dl>

        {recorded.status === 'unsent' && (
          <p className="flex items-center gap-2 text-sm text-warning">
            <CloudOff className="size-4 shrink-0" aria-hidden="true" />
            Couldn't reach the server, so this solve isn't recorded yet. It'll be sent when you're back online, with its time as you solved it.
          </p>
        )}

        {isRecorded && <NamePrompt />}
        {streak && streak.current >= STREAK_WORTH_KEEPING && <GooglePrompt streak={streak.current} />}

        <div className="grid gap-2 sm:flex sm:items-end">
          <pre className="min-w-0 flex-1 overflow-x-auto rounded-field bg-base-200 px-3 py-2 font-sans text-sm whitespace-pre-wrap" aria-label="Share line">
            {share}
          </pre>
          <div className="flex justify-end gap-2">
            <CopyButton text={share} />
            <button type="button" className="btn btn-sm btn-primary" onClick={onExit}>
              More puzzles
            </button>
          </div>
        </div>

        {/* Once the solve is saved (or couldn't be), so the player's own time is on it. */}
        {puzzle.dailyDate && dailyDifficulty && recorded.status !== 'saving' && (
          <div className="border-t border-base-300 pt-4">
            <LeaderboardView date={puzzle.dailyDate} difficulty={dailyDifficulty} title="Leaderboard" />
            <Link to={`/leaderboard/${puzzle.dailyDate}?difficulty=${dailyDifficulty}`} className="link link-primary mt-2 inline-block text-sm">
              Today's other leaderboards
            </Link>
          </div>
        )}
      </div>
    </section>
  );
}

function timeNote(recorded: Recorded): string | undefined {
  switch (recorded.status) {
    case 'saving':
      return 'Saving…';
    case 'recorded':
      return recorded.late ? 'Recorded by the server, sent late' : 'Recorded by the server';
    default:
      return undefined;
  }
}

function Stat({ icon, label, value, note }: { icon: ReactNode; label: string; value: string; note?: string }) {
  return (
    <div className="grid gap-0.5">
      <dt className="flex items-center gap-1 text-xs font-semibold tracking-wide text-base-content/60 uppercase">
        {icon} {label}
      </dt>
      <dd className="text-2xl font-bold tabular-nums">{value}</dd>
      {note && <dd className="text-xs text-base-content/60">{note}</dd>}
    </div>
  );
}

/** The player's streak, fetched once the solve is recorded (it's computed from recorded solves). */
function useStreak(ready: boolean): Streak | null {
  const [streak, setStreak] = useState<Streak | null>(null);

  useEffect(() => {
    if (!ready) return;
    const controller = new AbortController();
    const now = new Date();
    fetchStreak(controller.signal, now)
      .then((s) => {
        setStreak(s);
        saveStreak({ ...s, asOf: localDate(now) });
      })
      .catch(() => {
        // The streak is a nice extra here; without it the result still stands.
      });
    return () => controller.abort();
  }, [ready]);

  return streak;
}

function StreakStat({ streak }: { streak: Streak }) {
  return (
    <div className="grid gap-0.5">
      <dt className="flex items-center gap-1 text-xs font-semibold tracking-wide text-base-content/60 uppercase">
        <Flame className="size-4" aria-hidden="true" /> Streak
      </dt>
      <dd className="text-2xl font-bold tabular-nums">
        {streak.current} day{streak.current === 1 ? '' : 's'}
      </dd>
      <dd className="text-xs text-base-content/60">Best {streak.best}</dd>
    </div>
  );
}

type GoogleState = { status: 'hidden' } | { status: 'asking' } | { status: 'done'; switched: boolean };

/**
 * Suggests signing in with Google to a player without an account, once their streak is worth
 * keeping: until then it lives in this browser's storage only. "Not now" is remembered.
 */
function GooglePrompt({ streak }: { streak: number }) {
  const [state, setState] = useState<GoogleState>({ status: 'hidden' });

  useEffect(() => {
    if (isGooglePromptDismissed()) return;
    let cancelled = false;
    void currentAccount().then((account) => {
      if (!cancelled && account?.isAnonymous) setState({ status: 'asking' });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  if (state.status === 'hidden') return null;
  if (state.status === 'done') {
    return (
      <p className="flex items-center gap-1.5 text-sm text-success" role="status">
        <ShieldCheck className="size-4 shrink-0" aria-hidden="true" />
        {state.switched
          ? 'Signed in. That Google account already had a player, so this browser uses it now.'
          : 'Signed in. Your streak is saved to your Google account.'}
      </p>
    );
  }
  return (
    <div className="grid gap-2 rounded-box bg-base-200 p-3">
      <p className="text-sm">
        <strong>Keep your {streak}-day streak.</strong> It's only saved in this browser for now. Sign in with Google so clearing your browser data doesn't lose it, and to play on other devices.
      </p>
      <div className="flex flex-wrap items-start gap-2">
        <GoogleButton
          className="btn btn-sm"
          onDone={(result) => {
            if (result === 'switched') clearPlayerCaches();
            setState({ status: 'done', switched: result === 'switched' });
          }}
        />
        <button
          type="button"
          className="btn btn-sm btn-ghost"
          onClick={() => {
            dismissGooglePrompt();
            setState({ status: 'hidden' });
          }}
        >
          Not now
        </button>
      </div>
    </div>
  );
}

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      // No clipboard access (an old browser, or not allowed): the line is on screen to copy by hand.
      setCopied(false);
    }
  };
  return (
    <button type="button" className="btn btn-sm" onClick={copy}>
      {copied ? <Check className="size-4 shrink-0" aria-hidden="true" /> : <Copy className="size-4 shrink-0" aria-hidden="true" />}
      {copied ? 'Copied' : 'Copy'}
    </button>
  );
}

type NameState = { status: 'hidden' } | { status: 'asking'; saving: boolean; error: string | null } | { status: 'saved'; name: string };

/**
 * Asks for a display name, for friends and leaderboards, if the player has none and hasn't said
 * no before. Shown with the first recorded result.
 */
function NamePrompt() {
  const [state, setState] = useState<NameState>({ status: 'hidden' });
  const [name, setName] = useState('');

  useEffect(() => {
    if (isNamePromptDismissed()) return;
    const controller = new AbortController();
    fetchPlayer(controller.signal)
      .then((player) => {
        if (player.displayName === null) setState({ status: 'asking', saving: false, error: null });
      })
      .catch(() => {
        // Ask another time.
      });
    return () => controller.abort();
  }, []);

  if (state.status === 'hidden') return null;
  if (state.status === 'saved') {
    return (
      <p className="flex items-center gap-1.5 text-sm text-success" role="status">
        <Check className="size-4 shrink-0" aria-hidden="true" />
        <span>
          Friends will see you as {state.name}. Change it any time under{' '}
          <Link to="/me" className="link">
            You &amp; friends
          </Link>
          .
        </span>
      </p>
    );
  }

  const save = (e: FormEvent) => {
    e.preventDefault();
    setState({ status: 'asking', saving: true, error: null });
    updateDisplayName(name)
      .then((player) => setState({ status: 'saved', name: player.displayName ?? name.trim() }))
      .catch((err: unknown) => setState({ status: 'asking', saving: false, error: err instanceof ApiError ? err.message : String(err) }));
  };

  return (
    <form className="grid gap-2 rounded-box bg-base-200 p-3" onSubmit={save}>
      <label htmlFor="display-name" className="text-sm font-semibold">
        What should friends see you as?
      </label>
      <div className="flex flex-wrap gap-2">
        {/* text-base (16px) so iOS doesn't zoom in when the field is focused. */}
        <input
          id="display-name"
          className="input input-sm min-w-0 flex-1 text-base"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={60}
          autoComplete="nickname"
          placeholder="Your name"
          aria-describedby={state.error ? 'display-name-error' : undefined}
        />
        <button type="submit" className="btn btn-sm btn-primary" disabled={state.saving || !name.trim()}>
          {state.saving && <span className="loading loading-spinner loading-xs" aria-hidden="true" />} Save
        </button>
        <button
          type="button"
          className="btn btn-sm btn-ghost"
          onClick={() => {
            dismissNamePrompt();
            setState({ status: 'hidden' });
          }}
        >
          Not now
        </button>
      </div>
      {state.error && (
        <p id="display-name-error" className="text-sm text-error" role="alert">
          {state.error}
        </p>
      )}
    </form>
  );
}
