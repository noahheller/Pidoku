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
    <main className="game">
      <header className="game-header">
        <button type="button" className="link" onClick={onExit}>
          ← Puzzles
        </button>
        <h1>
          {puzzle.name}
          {puzzle.difficulty && puzzle.difficulty !== puzzle.name && <span className="badge">{puzzle.difficulty}</span>}
        </h1>
        <div className="stats" aria-live="off">
          <span title="Time">⏱ {formatTime(elapsedMs)}</span>
          {puzzle.solution && <span title="Mistakes">✗ {mistakes}</span>}
        </div>
      </header>

      {completed && <CompletionBanner puzzle={puzzle} elapsedMs={elapsedMs} mistakes={mistakes} onExit={onExit} />}

      <div className="play-area">
        <Board state={state} showErrors={settings.showMistakes} onSelect={onSelect} />
        <div className="side-panel">
          <NumberPad state={state} dispatch={dispatch} />
          <Controls state={state} dispatch={dispatch} showErrors={settings.showMistakes} onToggleErrors={toggleErrors} />
          <RulesHint rules={rules} />
        </div>
      </div>
      {!completed && <p className="shortcuts">Arrows move · digits fill · Shift+digit notes · N notes mode · Backspace erase · Ctrl+Z undo</p>}
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

function RulesHint({ rules }: { rules: Rules }) {
  const repeated = rules.digits.filter((d) => rules.frequency[d] > 1);
  return (
    <p className="rules-hint">
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
    <section className="banner" role="status">
      <strong>Solved!</strong> <span>Finished {result}.</span>
      <div className="banner-actions">
        <button type="button" onClick={copy}>
          {copied ? 'Copied ✓' : 'Copy result'}
        </button>
        <button type="button" className="primary" onClick={onExit}>
          More puzzles
        </button>
      </div>
    </section>
  );
}
