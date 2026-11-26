import { ArrowRight, Check, CircleAlert, CircleX, CloudOff, Copy, Flame, Lightbulb, PencilLine, Share2, ShieldCheck, Sparkles, Timer, Trophy } from 'lucide-react';
import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import { currentAccount } from '../api/auth';
import { recordEvent } from '../api/events';
import { ApiError, DAILY_DIFFICULTIES, fetchDailyOn, fetchPlayer, fetchStreak, localDate, updateDisplayName, type Streak } from '../api/client';
import { dailyNumber, nextUnsolved, shareLine, shareUrl } from '../game/daily';
import { formatPenalty, formatTime } from '../game/format';
import { canAutoNote } from '../game/game';
import { mistakePenaltyMs } from '../game/penalties';
import type { PuzzleDef } from '../game/puzzleFile';
import { clearPlayerCaches, dismissGooglePrompt, dismissNamePrompt, isGooglePromptDismissed, isNamePromptDismissed, loadProgress, saveStreak } from '../game/storage';
import { LeaderboardView } from './Leaderboard';
import { GoogleButton } from './Profile';

/** A streak this long is worth keeping: past it, players without an account are asked to sign in with Google. */
const STREAK_WORTH_KEEPING = 3;

/** What the server knows about this solve. */
export type Recorded =
  /** Not a puzzle from the API, or solved on an earlier visit without a saved result. */
  | { status: 'none' }
  | { status: 'saving' }
  /** The API has it: `elapsedMs` is the ranked time it saved (the game's timer). */
  | { status: 'recorded'; elapsedMs: number; late: boolean }
  /** Couldn't be sent yet; it's kept and sent when the connection comes back. */
  | { status: 'unsent' }
  /**
   * Will never be recorded: the attempt never started (no connection or no sign-in when the puzzle
   * was opened, so the server has no start time), or the API refused the solve.
   */
  | { status: 'unrecorded'; reason: 'not-started' | 'rejected' };

interface ResultProps {
  puzzle: PuzzleDef;
  /** The game's own timer, shown when the server's time isn't known. */
  elapsedMs: number;
  mistakes: number;
  /** Auto notes was used (only offered on Expert and Fiendish, where the result says either way). */
  autoNotesUsed: boolean;
  /** Hint steps revealed. */
  hintsUsed: number;
  /** What the leaderboards add to the time for mistakes and hints; 0 for a puzzle that isn't ranked. */
  penaltyMs: number;
  recorded: Recorded;
  onExit: () => void;
}

/**
 * Shown above the board once the puzzle is solved: the time (as saved on the server, when it has it),
 * mistakes, for a daily the streak, the leaderboard and a button to the next daily, and a line to
 * share with friends. The first time a solve is recorded, it also asks for the name friends will
 * see; once there's a streak worth keeping, it suggests signing in with Google.
 */
export function Result({ puzzle, elapsedMs, mistakes, autoNotesUsed, hintsUsed, penaltyMs, recorded, onExit }: ResultProps) {
  const isRecorded = recorded.status === 'recorded';
  const streak = useStreak(isRecorded && puzzle.dailyDate !== undefined);
  const dailyDifficulty = DAILY_DIFFICULTIES.find((d) => d === puzzle.difficulty?.toLowerCase());
  const time = isRecorded ? recorded.elapsedMs : elapsedMs;
  const url = puzzle.shareCode ? shareUrl(puzzle.shareCode) : null;
  const shareDetails = {
    difficulty: puzzle.difficulty,
    dailyDate: puzzle.dailyDate ?? null,
    elapsedMs: time,
    mistakes: puzzle.solution ? mistakes : null,
    autoNotesUsed,
    penaltyMs,
    hintsUsed,
  };
  const share = shareLine({ ...shareDetails, url });
  // Share taps are counted (analytics), to see whether sharing is what brings players.
  const shared = (method: 'share' | 'copy') =>
    recordEvent('share', { puzzleId: puzzle.apiId, detail: { method, daily: puzzle.dailyDate !== undefined } });

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
          <Stat
            icon={<Timer className="size-4" aria-hidden="true" />}
            label="Time"
            value={formatTime(time)}
            extra={penaltyMs > 0 ? formatPenalty(penaltyMs) : undefined}
            note={penaltyMs > 0 ? `Ranked as ${formatTime(time + penaltyMs)}${timeNote(recorded) ? ` · ${timeNote(recorded)}` : ''}` : timeNote(recorded)}
          />
          {puzzle.solution && (
            <Stat
              icon={<CircleX className="size-4" aria-hidden="true" />}
              label="Mistakes"
              value={String(mistakes)}
              note={penaltyMs > 0 && mistakes > 0 ? formatPenalty(mistakePenaltyMs(mistakes)) : undefined}
            />
          )}
          {hintsUsed > 0 && (
            <Stat
              icon={<Lightbulb className="size-4" aria-hidden="true" />}
              label="Hints"
              value={String(hintsUsed)}
              note={`${hintsUsed === 1 ? 'step' : 'steps'} · ${formatPenalty(penaltyMs - mistakePenaltyMs(mistakes))}`}
            />
          )}
          {canAutoNote(puzzle) && (
            <Stat
              icon={autoNotesUsed ? <Sparkles className="size-4" aria-hidden="true" /> : <PencilLine className="size-4" aria-hidden="true" />}
              label="Notes"
              value={autoNotesUsed ? 'Auto' : 'By hand'}
              note={autoNotesUsed ? 'Used auto notes' : 'No auto notes'}
            />
          )}
          {puzzle.dailyDate && streak && <StreakStat streak={streak} />}
        </dl>

        {recorded.status === 'unsent' && (
          <p className="flex items-center gap-2 text-sm text-warning">
            <CloudOff className="size-4 shrink-0" aria-hidden="true" />
            Couldn't reach the server, so this solve isn't recorded yet. It'll be sent when you're back online, with its time as you solved it.
          </p>
        )}
        {recorded.status === 'unrecorded' && (
          <p className="flex items-center gap-2 text-sm text-warning">
            {recorded.reason === 'not-started' ? (
              <CloudOff className="size-4 shrink-0" aria-hidden="true" />
            ) : (
              <CircleAlert className="size-4 shrink-0" aria-hidden="true" />
            )}
            {unrecordedMessage(recorded.reason, puzzle.dailyDate !== undefined)}
          </p>
        )}

        {isRecorded && <NamePrompt />}
        {streak && streak.current >= STREAK_WORTH_KEEPING && <GooglePrompt streak={streak.current} />}

        <div className="grid gap-2 sm:flex sm:items-end">
          <pre className="min-w-0 flex-1 overflow-x-auto rounded-field bg-base-200 px-3 py-2 font-sans text-sm whitespace-pre-wrap" aria-label="Share line">
            {share}
          </pre>
          <div className="flex flex-wrap justify-end gap-2">
            <ShareButton text={shareLine({ ...shareDetails, url: null })} url={url} onShared={() => shared('share')} />
            <CopyButton text={share} onCopied={() => shared('copy')} />
            {puzzle.dailyDate ? (
              <NextDailyButton puzzle={puzzle} onExit={onExit} />
            ) : (
              <button type="button" className="btn btn-sm btn-primary" onClick={onExit}>
                More puzzles
              </button>
            )}
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
        {/* Any other puzzle from the API has a board too: sharing it is a challenge. */}
        {!puzzle.dailyDate && puzzle.shareCode && recorded.status !== 'saving' && (
          <div className="border-t border-base-300 pt-4">
            <LeaderboardView shareCode={puzzle.shareCode} title="Leaderboard" />
            <p className="mt-2 text-sm text-base-content/60">Share it to challenge your friends: they'll see the time to beat.</p>
          </div>
        )}
      </div>
    </section>
  );
}

/** Why a solve won't be recorded, and what that means for the player: nothing to retry. */
function unrecordedMessage(reason: 'not-started' | 'rejected', daily: boolean): string {
  const consequence = daily ? "isn't on the leaderboard and doesn't count toward your streak" : "wasn't saved to your account";
  return reason === 'not-started'
    ? `This solve ${consequence}: the game couldn't reach the server or sign you in when you started the puzzle, so there was no start time to time it from.`
    : `The server didn't accept this solve, so it ${consequence}.`;
}

function timeNote(recorded: Recorded): string | undefined {
  switch (recorded.status) {
    case 'saving':
      return 'Saving…';
    case 'recorded':
      return recorded.late ? 'Saved, sent late' : 'Saved';
    default:
      return undefined;
  }
}

function Stat({ icon, label, value, extra, note }: { icon: ReactNode; label: string; value: string; extra?: string; note?: string }) {
  return (
    <div className="grid gap-0.5">
      <dt className="flex items-center gap-1 text-xs font-semibold tracking-wide text-base-content/60 uppercase">
        {icon} {label}
      </dt>
      <dd className="text-2xl font-bold tabular-nums">
        {value}
        {extra && <span className="ml-1 text-base font-semibold text-warning">{extra}</span>}
      </dd>
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

/**
 * Straight on to the next of the date's dailies not yet solved here ("Next: Daily Medium"), so one
 * solve leads to the next. "More puzzles" once they're all solved, or when they can't be fetched
 * (an old daily from a share link: the API only serves dates that are today somewhere).
 */
function NextDailyButton({ puzzle, onExit }: { puzzle: PuzzleDef; onExit: () => void }) {
  const navigate = useNavigate();
  const [next, setNext] = useState<{ status: 'loading' } | { status: 'done'; puzzle: PuzzleDef | null }>({ status: 'loading' });

  useEffect(() => {
    const controller = new AbortController();
    fetchDailyOn(puzzle.dailyDate!, controller.signal)
      .then(({ puzzles }) => setNext({ status: 'done', puzzle: nextUnsolved(puzzles, puzzle.id, (id) => loadProgress(id)?.completed === true) }))
      .catch(() => {
        if (!controller.signal.aborted) setNext({ status: 'done', puzzle: null });
      });
    return () => controller.abort();
  }, [puzzle.dailyDate, puzzle.id]);

  if (next.status === 'loading') {
    return (
      <button type="button" className="btn btn-sm btn-primary" disabled>
        <span className="loading loading-spinner loading-xs" aria-hidden="true" /> Next daily
      </button>
    );
  }
  const following = next.puzzle;
  if (!following?.shareCode) {
    return (
      <button type="button" className="btn btn-sm btn-primary" onClick={onExit}>
        More puzzles
      </button>
    );
  }
  return (
    <button
      type="button"
      className="btn btn-sm btn-primary"
      // The puzzle rides along in the history entry, as when it's opened from the home screen.
      onClick={() => void navigate(`/p/${following.shareCode}`, { state: { puzzle: following } })}
    >
      Next: {following.name} <ArrowRight className="size-4 shrink-0" aria-hidden="true" />
    </button>
  );
}

/** The phone's share sheet, where there is one: how a result gets into the group chat. */
function ShareButton({ text, url, onShared }: { text: string; url: string | null; onShared: () => void }) {
  if (typeof navigator.share !== 'function') return null;
  const share = () => {
    navigator.share(url ? { text, url } : { text }).then(onShared, () => {
      // Closed the share sheet, or sharing isn't allowed: Copy is right next to it.
    });
  };
  return (
    <button type="button" className="btn btn-sm" onClick={share}>
      <Share2 className="size-4 shrink-0" aria-hidden="true" /> Share
    </button>
  );
}

function CopyButton({ text, onCopied }: { text: string; onCopied: () => void }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      onCopied();
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
