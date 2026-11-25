import { ArrowLeft, CalendarDays, CircleX, Info, Pause, Play, Timer, X } from 'lucide-react';
import { useCallback, useEffect, useLayoutEffect, useReducer, useRef, useState, type RefObject } from 'react';
import { saveProgress as uploadProgress, startAttempt, type Attempt } from '../api/client';
import { recordCompletion } from '../api/completions';
import { flushEvents, recordEvent } from '../api/events';
import { dailyNumber } from '../game/daily';
import { countClues, formatDate, formatTime } from '../game/format';
import { gameReducer, newGame, toSaved, type GameAction, type GameState, type SavedProgress } from '../game/game';
import { actionForKey } from '../game/keys';
import type { PuzzleDef } from '../game/puzzleFile';
import { EMPTY, PI_DIGITS, type Rules } from '../game/rules';
import { isTutorialSeen, loadProgress, loadResult, saveProgress, saveResult } from '../game/storage';
import { boardChanged, newerOnServer, SYNC_INTERVAL_MS } from '../game/sync';
import { Board } from './Board';
import { Controls } from './Controls';
import { NumberPad } from './NumberPad';
import { Result, type Recorded } from './Result';
import { Tutorial } from './Tutorial';

interface GameProps {
  puzzle: PuzzleDef;
  /** Played before and being started over: restart the server's clock on any unfinished attempt. */
  replay?: boolean;
  /** Shown above the board, e.g. "You've played every hard puzzle. Here's one again." */
  notice?: string | null;
  onExit: () => void;
}

/**
 * A puzzle, after the tutorial if this browser has never seen it (the first puzzle anyone opens
 * here). Nothing starts, not the clock nor the attempt, until the tutorial is done or skipped.
 */
export function Game(props: GameProps) {
  const [tutorial, setTutorial] = useState(() => !isTutorialSeen());
  if (tutorial) return <Tutorial onDone={() => setTutorial(false)} doneLabel={`Start ${props.puzzle.name}`} />;
  return <PuzzleGame {...props} />;
}

function PuzzleGame({ puzzle, replay = false, notice = null, onExit }: GameProps) {
  const [state, dispatch] = useReducer(gameReducer, puzzle, (p) => newGame(p, loadProgress(p.id)));
  const { values, notes, mistakes, elapsedMs, completed, autoNotesUsed, rules } = state;
  // When the board (not just the clock) last changed here, to tell this browser's copy from a newer
  // one saved on another device. 0 for a board never touched in this browser.
  const boardChangedAt = useRef<number>(loadProgress(puzzle.id)?.savedAt ?? 0);
  // Compared by identity, so React re-running the effect (StrictMode) doesn't count as a change.
  const lastBoard = useRef({ values, notes, mistakes });
  useEffect(() => {
    const last = lastBoard.current;
    if (last.values === values && last.notes === notes && last.mistakes === mistakes) return;
    lastBoard.current = { values, notes, mistakes };
    boardChangedAt.current = Date.now();
  }, [values, notes, mistakes]);
  const [synced, setSynced] = useState(false);
  // A daily waits on its start screen until the player presses Start: its ranked time is the
  // server's wall-clock time, so the start has to be deliberate. Once started (there's saved
  // progress) it opens straight to the board. Other puzzles start when opened.
  const [ready, setReady] = useState(() => !puzzle.dailyDate || loadProgress(puzzle.id) !== null);
  const [recorded, setRecorded] = useState<Recorded>(() => {
    const saved = loadResult(puzzle.id);
    return saved ? { status: 'recorded', ...saved } : { status: 'none' };
  });

  useEffect(() => {
    // Nothing saved before Start, or the start screen would be skipped next time.
    if (ready) saveProgress(puzzle.id, { values, notes, mistakes, elapsedMs, completed, autoNotesUsed, savedAt: boardChangedAt.current });
  }, [ready, puzzle.id, values, notes, mistakes, elapsedMs, completed, autoNotesUsed]);

  // Records the game on the server, for puzzles that came from the API. The attempt starts when
  // the puzzle is opened unsolved, or for a daily when Start is pressed (the API hands back the
  // unfinished attempt if there is one, so reopening doesn't restart its clock) and completes when
  // the puzzle is solved in this visit.
  // A failed start is ignored: the game plays the same when the API or sign-in is unavailable,
  // it just isn't recorded. A failed completion is retried later (see api/completions.ts).
  const attempt = useRef<Promise<Attempt | null> | null>(null);
  // Only the first start of a replay restarts the clock; later ones (StrictMode's second run of
  // the effect) continue that attempt.
  const restartOnStart = useRef(replay);
  const latest = useRef({ values, mistakes, elapsedMs, autoNotesUsed });
  useEffect(() => {
    latest.current = { values, mistakes, elapsedMs, autoNotesUsed };
  }, [values, mistakes, elapsedMs, autoNotesUsed]);
  // The started attempt's id, once known, for saving progress to it.
  const attemptId = useRef<number | null>(null);
  // Kept current in a layout effect, which runs before any effect's cleanup: the cleanups that
  // run as the puzzle is solved (useProgressSync, useAbandonedEvent) then see it solved.
  const stateRef = useRef(state);
  useLayoutEffect(() => {
    stateRef.current = state;
  });
  useEffect(() => {
    const { apiId } = puzzle;
    if (apiId === undefined || !ready) return;
    if (!completed) {
      const restart = restartOnStart.current;
      restartOnStart.current = false;
      attempt.current = startAttempt(apiId, restart).catch(() => null);
      const live = { current: true };
      void attempt.current.then((started) => {
        if (!started || !live.current) return;
        attemptId.current = started.id;
        // Signed in now, so any analytics waiting for an account can go.
        void flushEvents();
        // Played on another device since this browser last saved: carry on from there.
        const local = toSaved(stateRef.current, boardChangedAt.current);
        const remote = restart ? null : newerOnServer(local, started);
        if (remote) {
          dispatch({ type: 'load', progress: remote });
          setSynced(true);
        }
      });
      return () => {
        live.current = false;
      };
    }
    const started = attempt.current;
    attempt.current = null;
    if (!started) return;
    const { values: grid, mistakes: wrong, elapsedMs: activeMs, autoNotesUsed: autoNotes } = latest.current;
    setRecorded({ status: 'saving' });
    void recordCompletion(started, { grid, mistakes: wrong, activeMs: Math.round(activeMs), autoNotesUsed: autoNotes }).then((done) => {
      switch (done.status) {
        case 'recorded': {
          // The game's timer is the ranked time; the server's wall-clock time only when it has none.
          const { attempt: saved } = done;
          const result = { elapsedMs: saved.activeMs ?? saved.elapsedMs ?? Math.round(activeMs), late: saved.late };
          saveResult(puzzle.id, result);
          setRecorded({ status: 'recorded', ...result });
          break;
        }
        case 'queued':
          // Kept and sent later: no server time to show yet.
          setRecorded({ status: 'unsent' });
          break;
        default:
          // Never started, or refused: nothing will reach the server, so don't promise it will.
          setRecorded({ status: 'unrecorded', reason: done.status });
      }
    });
  }, [puzzle, ready, completed]);


  // The clock. Leaderboards rank this time, so it's measured with performance.now() rather than
  // by counting interval ticks (browsers delay and throttle intervals, which made it run slow).
  // It runs only while the board is on screen: hiding the tab or leaving the game (the back
  // button, or any other navigation) pauses it, and it picks up from the saved time on return.
  // clockFrom is when the time not yet added to elapsedMs began, or null while paused.
  const clockFrom = useRef<number | null>(null);
  /** Takes the time since clockFrom, restarting the count from now. */
  const takeClock = useCallback(() => {
    if (clockFrom.current === null) return 0;
    const now = performance.now();
    const ms = Math.max(0, now - clockFrom.current);
    clockFrom.current = now;
    return ms;
  }, []);
  const flushClock = useCallback(() => {
    const ms = takeClock();
    if (ms > 0) dispatch({ type: 'tick', ms });
  }, [takeClock]);
  const running = ready && !completed;
  useEffect(() => {
    if (!running) return;
    const resume = () => {
      clockFrom.current = document.visibilityState === 'visible' ? performance.now() : null;
    };
    const onVisibility = () => {
      flushClock();
      resume();
    };
    resume();
    // Only refreshes the time shown; the time itself comes from performance.now().
    const timer = setInterval(flushClock, 1000);
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisibility);
      const ms = takeClock();
      clockFrom.current = null;
      if (ms > 0 && !stateRef.current.completed) {
        dispatch({ type: 'tick', ms });
        // Leaving the game unmounts it before the save effect could run, so save the paused time here.
        const current = stateRef.current;
        saveProgress(puzzle.id, { ...toSaved(current, boardChangedAt.current), elapsedMs: current.elapsedMs + ms });
      }
    };
  }, [running, flushClock, takeClock, puzzle.id]);

  useProgressSync({ puzzle, running, attemptId, stateRef, board: { values, notes, mistakes } });
  useAbandonedEvent({ puzzle, running, stateRef });

  /** Every move first counts the time up to it, so a solve's time ends at the last digit. */
  const play = useCallback(
    (action: GameAction) => {
      flushClock();
      dispatch(action);
    },
    [flushClock],
  );

  useEffect(() => {
    if (!ready) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;
      const action = actionForKey(event, rules);
      if (action) {
        event.preventDefault();
        play(action);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [rules, ready, play]);

  const onSelect = useCallback((index: number) => dispatch({ type: 'select', index }), []);

  // Restarting resets the timer on screen, so the server's clock for the attempt starts again too.
  const { apiId } = puzzle;
  const act = useCallback(
    (action: GameAction) => {
      if (action.type === 'restart' && apiId !== undefined) {
        attempt.current = startAttempt(apiId, true).catch(() => null);
        setSynced(false);
      }
      play(action);
    },
    [apiId, play],
  );

  return (
    <main className="mx-auto grid max-w-[1040px] gap-3 px-2 pt-2 pb-6 sm:gap-4 sm:px-4 sm:pt-4 sm:pb-8">
      <header className="flex flex-wrap items-center gap-4">
        <button
          type="button"
          className="btn btn-ghost btn-sm text-primary"
          onClick={onExit}
          title={running ? 'Back to puzzles. Your clock pauses until you come back.' : 'Back to puzzles'}
          aria-label={running ? 'Back to puzzles, pausing your clock' : 'Back to puzzles'}
        >
          <ArrowLeft className="size-4 shrink-0" aria-hidden="true" /> Puzzles
        </button>
        <h1 className="flex flex-1 items-center gap-2 text-xl font-bold sm:text-2xl">
          {puzzle.name}
          {puzzle.difficulty && puzzle.difficulty !== puzzle.name && <span className="badge badge-outline badge-sm font-semibold tracking-normal text-base-content/70">{puzzle.difficulty}</span>}
        </h1>
        <div className="flex gap-4 text-base-content/60 tabular-nums" aria-live="off">
          <span className="flex items-center gap-1" title="Time">
            <Timer className="size-4 shrink-0" aria-hidden="true" /> {formatTime(elapsedMs)}
          </span>
          {puzzle.solution && (
            <span className="flex items-center gap-1" title="Mistakes">
              <CircleX className="size-4 shrink-0" aria-hidden="true" /> {mistakes}
            </span>
          )}
        </div>
      </header>

      {notice && <Notice text={notice} />}
      {synced && <Notice text="Carried on from where you left off on another device." />}

      {!ready && <StartScreen puzzle={puzzle} onStart={() => setReady(true)} />}

      {completed && <Result puzzle={puzzle} elapsedMs={elapsedMs} mistakes={mistakes} autoNotesUsed={autoNotesUsed} recorded={recorded} onExit={onExit} />}

      {ready && <div className="flex flex-wrap items-start justify-center gap-6">
        <Board state={state} onSelect={onSelect} />
        <div className="grid w-full gap-4 sm:w-[min(100%,360px)]">
          <NumberPad state={state} dispatch={play} />
          <Controls state={state} dispatch={act} />
          {running && (
            <p className="flex items-center gap-1.5 text-sm text-base-content/60">
              <Pause className="size-4 shrink-0" aria-hidden="true" /> Going back to Puzzles or switching away pauses your clock.
            </p>
          )}
          <RulesHint rules={rules} />
        </div>
      </div>}
      {ready && !completed && <Shortcuts />}
    </main>
  );
}

interface SyncOptions {
  puzzle: PuzzleDef;
  /** The board is on screen and the puzzle unfinished. */
  running: boolean;
  attemptId: RefObject<number | null>;
  stateRef: RefObject<GameState>;
  /** The board as rendered, so a change schedules a save. */
  board: Pick<GameState, 'values' | 'notes' | 'mistakes'>;
}

/**
 * Saves the unfinished board to the server, so it follows the player to another device (see
 * game/sync.ts): at most every {@link SYNC_INTERVAL_MS} while they play, and straight away when
 * the page is hidden or closed or they leave the game (with `keepalive`, so it survives the tab
 * closing). Only puzzles from the API, once their attempt has started.
 */
function useProgressSync({ puzzle, running, attemptId, stateRef, board: { values, notes, mistakes } }: SyncOptions) {
  const lastSent = useRef<SavedProgress | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const send = useCallback(
    (keepalive: boolean) => {
      if (timer.current) clearTimeout(timer.current);
      timer.current = null;
      const id = attemptId.current;
      const state = stateRef.current;
      if (id === null || state.completed) return;
      const board = toSaved(state);
      const elapsedMs = Math.round(state.elapsedMs);
      // Nothing new: the board's the same and the clock has barely moved.
      if (!boardChanged(lastSent.current, board) && Math.abs(elapsedMs - (lastSent.current?.elapsedMs ?? 0)) < 5_000) return;
      lastSent.current = { ...board, elapsedMs };
      uploadProgress(id, { values: board.values, notes: board.notes, mistakes: board.mistakes, activeMs: elapsedMs, autoNotesUsed: board.autoNotesUsed ?? false }, keepalive).catch(() => {
        // Offline or signed out: this browser still has it, and the next save tries again.
        lastSent.current = null;
      });
    },
    [attemptId, stateRef],
  );

  // A move schedules a save, which picks up every move made before it goes.
  useEffect(() => {
    if (!running || puzzle.apiId === undefined || timer.current) return;
    timer.current = setTimeout(() => send(false), SYNC_INTERVAL_MS);
  }, [running, puzzle.apiId, values, notes, mistakes, send]);

  useEffect(() => {
    if (!running || puzzle.apiId === undefined) return;
    const onHide = () => {
      if (document.visibilityState === 'hidden') send(true);
    };
    const onPageHide = () => send(true);
    document.addEventListener('visibilitychange', onHide);
    window.addEventListener('pagehide', onPageHide);
    return () => {
      document.removeEventListener('visibilitychange', onHide);
      window.removeEventListener('pagehide', onPageHide);
      // Leaving the game (or solving it, when this sends nothing).
      send(true);
    };
  }, [running, puzzle.apiId, send]);
}

/**
 * Records an `abandoned` analytics event when the player leaves an unfinished API puzzle (back to
 * Puzzles, or closing the tab): how far they got. They may come back to it; the event says where
 * people stop, not that they never return. Ignores leaving within a few seconds of opening.
 */
function useAbandonedEvent({ puzzle, running, stateRef }: Pick<SyncOptions, 'puzzle' | 'running' | 'stateRef'>) {
  useEffect(() => {
    if (!running || puzzle.apiId === undefined) return;
    const apiId = puzzle.apiId;
    const sent = { current: false };
    const record = (keepalive: boolean) => {
      const state = stateRef.current;
      if (sent.current || state.completed || state.elapsedMs < 5_000) return;
      sent.current = true;
      const filled = state.values.filter((v, i) => v !== EMPTY && puzzle.givens[i] === EMPTY).length;
      const empty = state.values.filter((v) => v === EMPTY).length;
      recordEvent('abandoned', { puzzleId: apiId, detail: { filled, empty, activeMs: Math.round(state.elapsedMs), mistakes: state.mistakes, daily: puzzle.dailyDate !== undefined } }, keepalive);
    };
    const onPageHide = () => record(true);
    window.addEventListener('pagehide', onPageHide);
    return () => {
      window.removeEventListener('pagehide', onPageHide);
      record(false);
    };
  }, [running, puzzle, stateRef]);
}

const SHORTCUTS: [keys: string[], action: string][] = [
  [['←', '→', '↑', '↓'], 'move'],
  [['1–9'], 'write'],
  [['Shift', '1–9'], 'pencil a note'],
  [['N'], 'notes mode'],
  [['⌫'], 'erase'],
  [['Ctrl', 'Z'], 'undo'],
];

function Shortcuts() {
  return (
    <ul className="hidden flex-wrap justify-center gap-x-5 gap-y-2 text-xs text-base-content/70 sm:flex" aria-label="Keyboard shortcuts">
      {SHORTCUTS.map(([keys, action]) => (
        <li key={action} className="flex items-center gap-1">
          {keys.map((k) => (
            <kbd key={k} className="kbd kbd-xs">
              {k}
            </kbd>
          ))}
          <span className="ml-0.5">{action}</span>
        </li>
      ))}
    </ul>
  );
}

function RulesHint({ rules }: { rules: Rules }) {
  const repeated = rules.digits.filter((d) => rules.frequency[d] > 1);
  return (
    <p className="text-sm text-base-content/60">
      Every row, column and box holds the first {rules.size} digits of π: {PI_DIGITS.slice(0,rules.size).join(' ')}.
      {repeated.length > 0 && (
        <>
          {' '}
          <strong>{repeated.join(' and ')}</strong> appear{repeated.length === 1 ? 's' : ''} twice.
        </>
      )}
    </p>
  );
}

function Notice({ text }: { text: string }) {
  const [shown, setShown] = useState(true);
  if (!shown) return null;
  return (
    <div role="status" className="alert alert-soft alert-info">
      <Info className="size-5 shrink-0" aria-hidden="true" />
      <span>{text}</span>
      <button type="button" className="btn btn-ghost btn-xs btn-circle" aria-label="Dismiss" onClick={() => setShown(false)}>
        <X className="size-4" aria-hidden="true" />
      </button>
    </div>
  );
}

/**
 * A daily before it starts: what it is, and a Start button. The board stays hidden, so the clock
 * can't be beaten by studying it first.
 */
function StartScreen({ puzzle, onStart }: { puzzle: PuzzleDef; onStart: () => void }) {
  const date = puzzle.dailyDate!;
  return (
    <section className="card mx-auto w-full max-w-md border border-base-300 bg-base-100 shadow-sm" aria-labelledby="start-title">
      <div className="card-body items-center gap-4 p-6 text-center sm:p-8">
        <p className="flex items-center gap-1.5 text-sm text-base-content/60">
          <CalendarDays className="size-4" aria-hidden="true" /> Pidoku #{dailyNumber(date)} · {formatDate(date)}
        </p>
        <h2 id="start-title" className="text-3xl font-bold">
          {puzzle.difficulty}
        </h2>
        <p className="text-sm text-base-content/60">{countClues(puzzle.givens)} clues</p>
        <p className="text-base-content/80">
          The clock starts when you press Start. It runs while the puzzle is on screen and pauses when you go back to
          Puzzles or switch to another tab, then carries on when you return.
        </p>
        <button type="button" className="btn btn-primary btn-lg w-full" onClick={onStart} autoFocus>
          <Play className="size-5" aria-hidden="true" /> Start
        </button>
      </div>
    </section>
  );
}
