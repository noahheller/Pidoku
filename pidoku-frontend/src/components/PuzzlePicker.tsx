import { CircleAlert, CircleCheck, ClipboardPaste, FileBraces, Upload, X } from 'lucide-react';
import { useState } from 'react';
import { countClues, formatTime } from '../game/format';
import { parsePuzzleJson, PuzzleFormatError, type PuzzleDef } from '../game/puzzleFile';
import { PI_DIGITS } from '../game/rules';
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
    <main className="mx-auto grid max-w-3xl gap-8 px-4 pt-8 pb-16">
      <header className="grid gap-4">
        <h1 className="text-4xl font-bold">
          Pidoku <span className="text-primary">π</span>
        </h1>
        <DigitRow />
        <p className="text-base-content/60">Sudoku with the digits of pi. Pick a puzzle or load one from the engine.</p>
      </header>

      <section>
        <h2 className="mb-3 text-lg font-semibold">Puzzles</h2>
        <PuzzleList puzzles={bundled} onPlay={onPlay} />
      </section>

      {custom.length > 0 && (
        <section>
          <h2 className="mb-3 text-lg font-semibold">Your puzzles</h2>
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

const ROW_DIGITS = PI_DIGITS.slice(0, 9);
const REPEATED = new Set(ROW_DIGITS.filter((d, i) => ROW_DIGITS.indexOf(d) !== i));

/** One row of a Pidoku board, written in digit by digit when the page loads. */
function DigitRow() {
  return (
    <div
      className="grid w-full max-w-[30rem] grid-cols-9 overflow-hidden rounded-sm border-3 border-base-content bg-base-100"
      role="img"
      aria-label={`A row of Pidoku: ${ROW_DIGITS.join(' ')}`}
    >
      {ROW_DIGITS.map((d, i) => (
        <span
          key={i}
          className={[
            'grid aspect-square place-items-center text-2xl font-bold sm:text-3xl',
            'motion-safe:animate-write-in',
            i < 8 && (i % 3 === 2 ? 'border-r-2 border-r-base-content' : 'border-r border-r-base-300'),
            REPEATED.has(d) && 'bg-primary/20',
          ]
            .filter(Boolean)
            .join(' ')}
          style={{ animationDelay: `${120 + i * 70}ms` }}
        >
          {d}
        </span>
      ))}
    </div>
  );
}

function PuzzleList({ puzzles, onPlay, onRemove }: { puzzles: PuzzleDef[]; onPlay: (p: PuzzleDef) => void; onRemove?: (id: string) => void }) {
  return (
    <ul className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-3">
      {puzzles.map((puzzle) => (
        <li key={puzzle.id} className="relative">
          <button
            type="button"
            className="card card-border h-full w-full cursor-pointer gap-1 border-base-300 bg-base-100 p-4 text-left transition hover:border-primary hover:shadow-md motion-safe:hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-primary"
            onClick={() => onPlay(puzzle)}
          >
            <span className="text-lg font-semibold">{puzzle.name}</span>
            <span className="text-sm text-base-content/60">
              {countClues(puzzle.givens)} clues
              {puzzle.score !== null && `. Difficulty score ${puzzle.score}`}
            </span>
            <ProgressBadge id={puzzle.id} />
          </button>
          {onRemove && (
            <button type="button" className="btn btn-circle btn-ghost btn-xs absolute top-2 right-2 text-base-content/60 hover:bg-error/10 hover:text-error" aria-label={`Remove ${puzzle.name}`} onClick={() => onRemove(puzzle.id)}>
              <X className="size-4" aria-hidden="true" />
            </button>
          )}
        </li>
      ))}
    </ul>
  );
}

function ProgressBadge({ id }: { id: string }) {
  const progress = loadProgress(id);
  if (!progress) return <span className="text-xs font-semibold text-base-content/60">New</span>;
  if (progress.completed) return <span className="text-xs font-semibold text-success">Solved · {formatTime(progress.elapsedMs)}</span>;
  return <span className="text-xs font-semibold text-primary">In progress · {formatTime(progress.elapsedMs)}</span>;
}

function LoadPuzzle({ onLoaded }: { onLoaded: (puzzles: PuzzleDef[]) => void }) {
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);

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
    setNotice(null);
    Promise.resolve()
      .then(load)
      .then((puzzles) => {
        setError(null);
        setText('');
        // A single puzzle opens straight away; several are added to "Your puzzles".
        if (puzzles.length > 1) setNotice(`Added ${puzzles.length} puzzles to Your puzzles.`);
        onLoaded(puzzles);
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)));
  };

  const onFiles = (fileList: FileList | null) => {
    // Copy now: a FileList is live and empties when the input is reset after onChange.
    const files = Array.from(fileList ?? []);
    if (files.length === 0) return;
    handle(() =>
      Promise.all(
        files.map(async (file) => {
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

  const loadPasted = () => {
    if (text.trim()) handle(() => [parse(text, 'Pasted puzzle')]);
  };

  return (
    <section className="card border border-base-300 bg-base-100 shadow-sm" aria-labelledby="load-puzzle-title">
      <div className="card-body gap-5 p-5 sm:p-6">
        <header className="flex items-start gap-3">
          <div className="grid size-10 shrink-0 place-items-center rounded-field bg-primary/10 text-primary">
            <FileBraces className="size-5" aria-hidden="true" />
          </div>
          <div>
            <h2 id="load-puzzle-title" className="text-lg leading-tight font-semibold">
              Load a puzzle
            </h2>
            <p className="mt-0.5 text-sm text-base-content/60">Play a board straight from the engine, or one a friend sent you.</p>
          </div>
        </header>
        <label
          className={[
            'flex cursor-pointer flex-col items-center gap-2 rounded-box border-2 border-dashed px-4 py-8 text-center transition-colors',
            'focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-primary',
            dragging ? 'border-primary bg-primary/10' : 'border-base-content/20 hover:border-primary hover:bg-primary/5',
          ].join(' ')}
          onDragEnter={(e) => {
            e.preventDefault();
            setDragging(true);
          }}
          onDragOver={(e) => e.preventDefault()}
          onDragLeave={(e) => {
            // Ignore leave events fired when moving between the label's own children.
            if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false);
          }}
          onDrop={(e) => {
            e.preventDefault();
            setDragging(false);
            onFiles(e.dataTransfer.files);
          }}
        >
          <span className={`grid size-12 place-items-center rounded-full transition-colors ${dragging ? 'bg-primary text-primary-content' : 'bg-base-200 text-primary'}`}>
            <Upload className="size-6" aria-hidden="true" />
          </span>
          <span className="font-medium">
            {dragging ? 'Drop to load' : (
              <>
                Drop JSON files here or <span className="text-primary underline underline-offset-2">browse</span>
              </>
            )}
          </span>
          <span className="text-xs text-base-content/60">
            Engine <code>*-stats.json</code> files work as-is. You can add several at once.
          </span>
          <input
            type="file"
            className="sr-only"
            accept=".json,application/json"
            multiple
            onChange={(e) => {
              onFiles(e.target.files);
              // Reset so choosing the same file again still fires onChange.
              e.target.value = '';
            }}
          />
        </label>

        <div className="divider my-0 text-xs text-base-content/50">or paste JSON</div>

        <div className="grid gap-3">
          <textarea
            className="textarea w-full font-mono text-sm leading-relaxed"
            aria-label="Puzzle JSON"
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                e.preventDefault();
                loadPasted();
              }
            }}
            placeholder={'{\n  "puzzle":   [[0, 0, 4, …], …],\n  "solution": [[3, 1, 4, …], …]\n}'}
            rows={5}
            spellCheck={false}
          />
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-xs text-base-content/60">
              Needs a <code>"puzzle"</code> grid (0 = empty). Add a <code>"solution"</code> to track mistakes.
            </p>
            <div className="ml-auto flex gap-2">
              {text && (
                <button type="button" className="btn btn-ghost btn-sm" onClick={() => setText('')}>
                  Clear
                </button>
              )}
              <button type="button" className="btn btn-primary btn-sm" disabled={!text.trim()} onClick={loadPasted} title="Load pasted JSON (Ctrl+Enter)">
                <ClipboardPaste className="size-4" aria-hidden="true" /> Load puzzle
              </button>
            </div>
          </div>
        </div>

        {error && (
          <div className="alert alert-error alert-soft" role="alert">
            <CircleAlert className="size-5 shrink-0" aria-hidden="true" />
            <span>{error}</span>
          </div>
        )}
        {notice && (
          <div className="alert alert-success alert-soft" role="status">
            <CircleCheck className="size-5 shrink-0" aria-hidden="true" />
            <span>{notice}</span>
          </div>
        )}
      </div>
    </section>
  );
}
