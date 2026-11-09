import { useState } from 'react';
import { countClues, formatTime, humanizeStrategy } from '../game/format';
import { parsePuzzleJson, PuzzleFormatError, type PuzzleDef } from '../game/puzzleFile';
import { loadProgress } from '../game/storage';

interface PuzzlePickerProps {
  bundled: PuzzleDef[];
  custom: PuzzleDef[];
  onPlay: (puzzle: PuzzleDef) => void;
  onAddCustom: (puzzles: PuzzleDef[]) => void;
  onRemoveCustom: (id: string) => void;
}

export function PuzzlePicker({ bundled, custom, onPlay, onAddCustom, onRemoveCustom }: PuzzlePickerProps) {
  return (
    <main className="picker">
      <header className="picker-header">
        <h1>
          Pidoku <span className="pi">π</span>
        </h1>
        <p>Sudoku with the digits of pi. Pick a puzzle or load one from the engine.</p>
      </header>

      <section>
        <h2>Puzzles</h2>
        <PuzzleList puzzles={bundled} onPlay={onPlay} />
      </section>

      {custom.length > 0 && (
        <section>
          <h2>Your puzzles</h2>
          <PuzzleList puzzles={custom} onPlay={onPlay} onRemove={onRemoveCustom} />
        </section>
      )}

      <LoadPuzzle
        onLoaded={(puzzles) => {
          onAddCustom(puzzles);
          if (puzzles.length === 1) onPlay(puzzles[0]);
        }}
      />
    </main>
  );
}

function PuzzleList({ puzzles, onPlay, onRemove }: { puzzles: PuzzleDef[]; onPlay: (p: PuzzleDef) => void; onRemove?: (id: string) => void }) {
  return (
    <ul className="puzzle-list">
      {puzzles.map((puzzle) => (
        <li key={puzzle.id} className="puzzle-card">
          <button type="button" className="puzzle-card-main" onClick={() => onPlay(puzzle)}>
            <span className="puzzle-name">{puzzle.name}</span>
            <span className="puzzle-meta">
              {countClues(puzzle.givens)} clues
              {puzzle.score !== null && ` · score ${puzzle.score}`}
              {puzzle.hardestStrategy && ` · ${humanizeStrategy(puzzle.hardestStrategy)}`}
            </span>
            <ProgressBadge id={puzzle.id} />
          </button>
          {onRemove && (
            <button type="button" className="remove" aria-label={`Remove ${puzzle.name}`} onClick={() => onRemove(puzzle.id)}>
              ✕
            </button>
          )}
        </li>
      ))}
    </ul>
  );
}

function ProgressBadge({ id }: { id: string }) {
  const progress = loadProgress(id);
  if (!progress) return <span className="progress new">New</span>;
  if (progress.completed) return <span className="progress done">Solved · {formatTime(progress.elapsedMs)}</span>;
  return <span className="progress started">In progress · {formatTime(progress.elapsedMs)}</span>;
}

function LoadPuzzle({ onLoaded }: { onLoaded: (puzzles: PuzzleDef[]) => void }) {
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);

  const parse = (source: string, name: string): PuzzleDef => {
    let json: unknown;
    try {
      json = JSON.parse(source);
    } catch {
      throw new PuzzleFormatError(`${name} isn't valid JSON.`);
    }
    return parsePuzzleJson(json, { name });
  };

  const handle = (load: () => PuzzleDef[] | Promise<PuzzleDef[]>) => {
    Promise.resolve()
      .then(load)
      .then((puzzles) => {
        setError(null);
        setText('');
        onLoaded(puzzles);
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)));
  };

  const onFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    handle(() =>
      Promise.all(
        Array.from(files).map(async (file) => {
          const base = file.name.replace(/\.json$/i, '').replace(/^Pidoku-\d+-/, '').replace(/-stats$/, '');
          const name = base.charAt(0).toUpperCase() + base.slice(1);
          try {
            return parse(await file.text(), name);
          } catch (e) {
            throw new PuzzleFormatError(`${file.name}: ${e instanceof Error ? e.message : String(e)}`);
          }
        }),
      ),
    );
  };

  return (
    <section className="load-puzzle">
      <h2>Load a puzzle</h2>
      <p>
        Use any <code>*-stats.json</code> file from <code>pidoku-engine</code>'s <code>Main</code>, or paste JSON with a{' '}
        <code>"puzzle"</code> grid (0 = empty) and optionally a <code>"solution"</code>.
      </p>
      <label className="file-input">
        <input type="file" accept=".json,application/json" multiple onChange={(e) => onFiles(e.target.files)} />
        Choose JSON file…
      </label>
      <textarea value={text} onChange={(e) => setText(e.target.value)} placeholder='{ "puzzle": [[0, 0, 4, …], …], "solution": [[…]] }' rows={5} spellCheck={false} />
      <button type="button" className="primary" disabled={!text.trim()} onClick={() => handle(() => [parse(text, 'Pasted puzzle')])}>
        Load pasted JSON
      </button>
      {error && (
        <p className="error-message" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
