import { CircleAlert, CircleCheck, ClipboardPaste, FileBraces, RotateCw, Shuffle, Upload, X } from 'lucide-react';
import { useState } from 'react';
import { DIFFICULTIES, fetchRandom, type Difficulty } from '../api/client';
import { useDaily } from '../api/useDaily';
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
  const daily = useDaily();

  return (
    <main className="mx-auto grid max-w-3xl gap-8 px-4 pt-8 pb-16">
      <header className="grid gap-4">
        <h1 className="text-4xl font-bold">
          Pidoku <span className="text-primary">π</span>
        </h1>
        <DigitRow />
        <p className="text-base-content/60">Sudoku with the digits of pi. Play today's puzzles, a random one, or load your own.</p>
      </header>

      <section aria-labelledby="today-title">
        <div className="mb-3 flex flex-wrap items-baseline justify-between gap-x-3">
          <h2 id="today-title" className="text-lg font-semibold">
            Today
          </h2>
          {daily.state.status === 'loaded' && <span className="text-sm text-base-content/60">{formatDate(daily.state.daily.date)}</span>}
        </div>
        {daily.state.status === 'loading' && <PuzzleListSkeleton count={3} />}
        {daily.state.status === 'loaded' && <PuzzleList puzzles={daily.state.daily.puzzles} onPlay={onPlay} />}
        {daily.state.status === 'failed' && (
          <div role="alert" className="alert alert-soft alert-warning">
            <CircleAlert className="size-5" aria-hidden="true" />
            <span>{daily.state.message}</span>
            <button type="button" className="btn btn-sm" onClick={daily.retry}>
              <RotateCw className="size-4" aria-hidden="true" /> Retry
            </button>
          </div>
        )}
      </section>

      <RandomPuzzle
        onPlay={(puzzle) => {
          // Also kept in "Your puzzles", so leaving it doesn't lose it.
          onAddCustom([puzzle]);
          onPlay(puzzle);
        }}
      />

      {/* Bundled puzzles are the offline fallback: only shown when the API can't be reached. */}
      {daily.state.status === 'failed' && (
        <section aria-labelledby="offline-title">
          <h2 id="offline-title" className="text-lg font-semibold">
            Offline puzzles
          </h2>
          <p className="mb-3 text-sm text-base-content/60">These come with the game, so they work without a connection.</p>
          <PuzzleList puzzles={bundled} onPlay={onPlay} />
        </section>
      )}

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

function PuzzleListSkeleton({ count }: { count: number }) {
  return (
    <ul className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-3" aria-busy="true" aria-label="Loading puzzles">
      {Array.from({ length: count }, (_, i) => (
        <li key={i} className="card card-border gap-2 border-base-300 bg-base-100 p-4">
          <span className="skeleton h-6 w-24" />
          <span className="skeleton h-4 w-40" />
          <span className="skeleton h-3 w-10" />
        </li>
      ))}
    </ul>
  );
}

/** One button per difficulty; each fetches a random pool puzzle and starts it. */
function RandomPuzzle({ onPlay }: { onPlay: (puzzle: PuzzleDef) => void }) {
  const [loading, setLoading] = useState<Difficulty | null>(null);
  const [error, setError] = useState<string | null>(null);

  const play = (difficulty: Difficulty) => {
    setLoading(difficulty);
    setError(null);
    fetchRandom(difficulty)
      .then(onPlay)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
      .finally(() => setLoading(null));
  };

  return (
    <section aria-labelledby="random-title">
      <h2 id="random-title" className="text-lg font-semibold">
        Random puzzle
      </h2>
      <p className="mb-3 text-sm text-base-content/60">A fresh puzzle at the difficulty you pick.</p>
      <div className="flex flex-wrap gap-2">
        {DIFFICULTIES.map((difficulty) => (
          <button
            key={difficulty}
            type="button"
            // Same outline as the puzzle cards: a light border that turns primary on hover.
            className="btn btn-sm border-base-300 bg-base-100 capitalize shadow-none transition hover:border-primary hover:shadow-md"
            disabled={loading !== null}
            onClick={() => play(difficulty)}
          >
            {loading === difficulty ? <span className="loading loading-spinner loading-xs" aria-hidden="true" /> : <Shuffle className="size-4" aria-hidden="true" />}
            {difficulty}
          </button>
        ))}
      </div>
      {error && (
        <div role="alert" className="mt-3 alert alert-soft alert-error">
          <CircleAlert className="size-5" aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}
    </section>
  );
}

/**
 * "Monday, 16 November" for a YYYY-MM-DD date, in the player's locale. The date is already the
 * player's local date, so it's formatted as written: parsing and printing both in UTC keeps the player's
 * time zone from shifting it by a day.
 */
function formatDate(date: string): string {
  return new Date(`${date}T00:00:00Z`).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', timeZone: 'UTC' });
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
