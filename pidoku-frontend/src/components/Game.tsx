import { ArrowLeft, Check, CircleX, Copy, Timer, Trophy } from 'lucide-react';
import { useCallback, useEffect, useReducer, useState } from 'react';
import { formatTime } from '../game/format';
import { gameReducer, newGame, type GameAction } from '../game/game';
import type { PuzzleDef } from '../game/puzzleFile';
import {isPlayable, PI_DIGITS, type Rules} from '../game/rules';
import { loadProgress, loadSettings, saveProgress, saveSettings } from '../game/storage';
import { Board } from './Board';
import { Controls } from './Controls';
import { NumberPad } from './NumberPad';

interface GameProps {
  puzzle: PuzzleDef;
  onExit: () => void;
}

export function Game({ puzzle, onExit }: GameProps) {
  const [state, dispatch] = useReducer(gameReducer, puzzle, (p) => newGame(p, loadProgress(p.id)));
  const [settings, setSettings] = useState(loadSettings);
  const { values, notes, mistakes, elapsedMs, completed, rules } = state;

  useEffect(() => {
    saveProgress(puzzle.id, { values, notes, mistakes, elapsedMs, completed });
  }, [puzzle.id, values, notes, mistakes, elapsedMs, completed]);

  useEffect(() => {
    if (completed) return;
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') dispatch({ type: 'tick', ms: 1000 });
    }, 1000);
    return () => clearInterval(timer);
  }, [completed]);

  useEffect(() => {
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
  }, [rules]);

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
        <h1 className="flex flex-1 items-center gap-2 text-2xl font-bold">
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

      {completed && <CompletionBanner puzzle={puzzle} elapsedMs={elapsedMs} mistakes={mistakes} onExit={onExit} />}

      <div className="flex flex-wrap items-start justify-center gap-6">
        <Board state={state} showErrors={settings.showMistakes} onSelect={onSelect} />
        <div className="grid w-full gap-4 sm:w-[min(100%,360px)]">
          <NumberPad state={state} dispatch={dispatch} />
          <Controls state={state} dispatch={dispatch} showErrors={settings.showMistakes} onToggleErrors={toggleErrors} />
          <RulesHint rules={rules} />
        </div>
      </div>
      {!completed && <Shortcuts />}
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

function CompletionBanner({ puzzle, elapsedMs, mistakes, onExit }: { puzzle: PuzzleDef; elapsedMs: number; mistakes: number; onExit: () => void }) {
  const [copied, setCopied] = useState(false);
  const result = `in ${formatTime(elapsedMs)}${puzzle.solution ? ` with ${mistakes} mistake${mistakes === 1 ? '' : 's'}` : ''}`;
  const shareText = `I solved Pidoku ${puzzle.name} ${result} π`;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(shareText);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  return (
    <section className="alert alert-success alert-soft flex flex-wrap border-2 border-success" role="status">
      <strong className="flex items-center gap-1.5 text-lg">
        <Trophy className="size-5" aria-hidden="true" /> Solved!
      </strong> <span>Finished {result}.</span>
      <div className="ml-auto flex gap-2">
        <button type="button" className="btn btn-sm" onClick={copy}>
          {copied ? <Check className="size-4 shrink-0" aria-hidden="true" /> : <Copy className="size-4 shrink-0" aria-hidden="true" />}
          {copied ? 'Copied' : 'Copy result'}
        </button>
        <button type="button" className="btn btn-sm btn-primary" onClick={onExit}>
          More puzzles
        </button>
      </div>
    </section>
  );
}
