import { ArrowLeft, CalendarDays, CircleX, Info, Play, Timer, X } from 'lucide-react';
import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { startAttempt, type Attempt } from '../api/client';
import { recordCompletion } from '../api/completions';
import { dailyNumber } from '../game/daily';
import { countClues, formatDate, formatTime } from '../game/format';
import { gameReducer, newGame, type GameAction } from '../game/game';
import type { PuzzleDef } from '../game/puzzleFile';
import {isPlayable, PI_DIGITS, type Rules} from '../game/rules';
import { loadProgress, loadResult, loadSettings, saveProgress, saveResult, saveSettings } from '../game/storage';
import { Board } from './Board';
import { Controls } from './Controls';
import { NumberPad } from './NumberPad';
import { Result, type Recorded } from './Result';

interface GameProps {
  puzzle: PuzzleDef;
  /** Played before and being started over: restart the server's clock on any unfinished attempt. */
  replay?: boolean;
  /** Shown above the board, e.g. "You've played every hard puzzle. Here's one again." */
  notice?: string | null;
  onExit: () => void;
}

export function Game({ puzzle, replay = false, notice = null, onExit }: GameProps) {
  const [state, dispatch] = useReducer(gameReducer, puzzle, (p) => newGame(p, loadProgress(p.id)));
  const [settings, setSettings] = useState(loadSettings);
  const { values, notes, mistakes, elapsedMs, completed, rules } = state;
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
    if (ready) saveProgress(puzzle.id, { values, notes, mistakes, elapsedMs, completed });
  }, [ready, puzzle.id, values, notes, mistakes, elapsedMs, completed]);

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
  const latest = useRef({ values, mistakes, elapsedMs });
  useEffect(() => {
    latest.current = { values, mistakes, elapsedMs };
  }, [values, mistakes, elapsedMs]);
  useEffect(() => {
    const { apiId } = puzzle;
    if (apiId === undefined || !ready) return;
    if (!completed) {
      attempt.current = startAttempt(apiId, restartOnStart.current).catch(() => null);
      restartOnStart.current = false;
      return;
    }
    const started = attempt.current;
    attempt.current = null;
    if (!started) return;
    const { values: grid, mistakes: wrong, elapsedMs: activeMs } = latest.current;
    setRecorded({ status: 'saving' });
    void recordCompletion(started, { grid, mistakes: wrong, activeMs }).then((done) => {
      if (done?.elapsedMs != null) {
        const result = { elapsedMs: done.elapsedMs, late: done.late };
        saveResult(puzzle.id, result);
        setRecorded({ status: 'recorded', ...result });
      } else {
        // Kept and sent later (or refused); either way there's no server time to show yet.
        setRecorded({ status: 'unsent' });
      }
    });
  }, [puzzle, ready, completed]);

  // Restarting resets the timer on screen, so the server's clock for the attempt starts again too.
  const { apiId } = puzzle;
  const act = useCallback(
    (action: GameAction) => {
      if (action.type === 'restart' && apiId !== undefined) attempt.current = startAttempt(apiId, true).catch(() => null);
      dispatch(action);
    },
    [apiId],
  );

  useEffect(() => {
    if (completed || !ready) return;
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') dispatch({ type: 'tick', ms: 1000 });
    }, 1000);
    return () => clearInterval(timer);
  }, [completed, ready]);

  useEffect(() => {
    if (!ready) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;
      const action = actionForKey(event, rules);
      if (action) {
        event.preventDefault();
        dispatch(action);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [rules, ready]);

  const onSelect = useCallback((index: number) => dispatch({ type: 'select', index }), []);

  const toggleErrors = () => {
    const next = { ...settings, showMistakes: !settings.showMistakes };
    setSettings(next);
    saveSettings(next);
  };

  return (
    <main className="mx-auto grid max-w-[1040px] gap-3 px-2 pt-2 pb-6 sm:gap-4 sm:px-4 sm:pt-4 sm:pb-8">
      <header className="flex flex-wrap items-center gap-4">
        <button type="button" className="btn btn-ghost btn-sm text-primary" onClick={onExit}>
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

      {!ready && <StartScreen puzzle={puzzle} onStart={() => setReady(true)} />}

      {completed && <Result puzzle={puzzle} elapsedMs={elapsedMs} mistakes={mistakes} recorded={recorded} onExit={onExit} />}

      {ready && <div className="flex flex-wrap items-start justify-center gap-6">
        <Board state={state} showErrors={settings.showMistakes} onSelect={onSelect} />
        <div className="grid w-full gap-4 sm:w-[min(100%,360px)]">
          <NumberPad state={state} dispatch={dispatch} />
          <Controls state={state} dispatch={act} showErrors={settings.showMistakes} onToggleErrors={toggleErrors} />
          <RulesHint rules={rules} />
        </div>
      </div>}
      {ready && !completed && <Shortcuts />}
    </main>
  );
}

function actionForKey(event: KeyboardEvent, rules: Rules): GameAction | null {
  const mod = event.ctrlKey || event.metaKey;
  if (mod && event.key.toLowerCase() === 'z') return { type: event.shiftKey ? 'redo' : 'undo' };
  if (mod && event.key.toLowerCase() === 'y') return { type: 'redo' };
  if (mod || event.altKey) return null;

  switch (event.key) {
    case 'ArrowUp':
      return { type: 'move', dRow: -1, dCol: 0 };
    case 'ArrowDown':
      return { type: 'move', dRow: 1, dCol: 0 };
    case 'ArrowLeft':
      return { type: 'move', dRow: 0, dCol: -1 };
    case 'ArrowRight':
      return { type: 'move', dRow: 0, dCol: 1 };
    case 'Backspace':
    case 'Delete':
      return { type: 'erase' };
    case 'n':
    case 'N':
      return { type: 'toggleNotesMode' };
    case 'Escape':
      return { type: 'select', index: null };
  }

  // event.code, not event.key, so Shift+1 still reads as 1 rather than "!".
  const match = /^(?:Digit|Numpad)(\d)$/.exec(event.code);
  if (match) {
    const digit = Number(match[1]);
    if (isPlayable(rules, digit)) return { type: 'input', digit, asNote: event.shiftKey ? true : undefined };
  }
  return null;
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
          The clock starts when you press Start and keeps running until you solve it, even if you leave the page.
        </p>
        <button type="button" className="btn btn-primary btn-lg w-full" onClick={onStart} autoFocus>
          <Play className="size-5" aria-hidden="true" /> Start
        </button>
      </div>
    </section>
  );
}
