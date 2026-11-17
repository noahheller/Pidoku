import { CircleAlert, RotateCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router';
import { fetchByShareCode } from './api/client';
import { sendPendingCompletions } from './api/completions';
import { Game } from './components/Game';
import { PuzzlePicker, type PlayOptions } from './components/PuzzlePicker';
import type { PuzzleDef } from './game/puzzleFile';
import { clearProgress, loadCustomPuzzles, saveCustomPuzzles } from './game/storage';
import { bundledPuzzles } from './puzzles';

/**
 * Routes:
 * - `/`: the home screen.
 * - `/p/{shareCode}`: a puzzle from the API. This is the share link, so it works opened cold.
 * - `/local/{id}`: a puzzle that only exists in this browser (bundled, or loaded from a file).
 *
 * firebase.json serves index.html for every path, so these all load the app.
 */
function App() {
  const [custom, setCustom] = useState(loadCustomPuzzles);
  const navigate = useNavigate();

  // Solves the API couldn't be told about at the time (see api/completions.ts): try again now,
  // and whenever the connection comes back.
  useEffect(() => {
    const send = () => void sendPendingCompletions();
    send();
    window.addEventListener('online', send);
    return () => window.removeEventListener('online', send);
  }, []);

  const updateCustom = (next: PuzzleDef[]) => {
    setCustom(next);
    saveCustomPuzzles(next);
  };

  // The puzzle rides along in the history entry, so opening it from the home screen doesn't fetch
  // it again. A share link opened cold has no state and fetches it.
  const play = (puzzle: PuzzleDef, { replay = false, notice = null }: PlayOptions = {}) => {
    const path = puzzle.shareCode ? `/p/${puzzle.shareCode}` : `/local/${encodeURIComponent(puzzle.id)}`;
    void navigate(path, { state: { puzzle, replay, notice } satisfies HistoryState });
  };

  return (
    <Routes>
      <Route
        path="/"
        element={
          <PuzzlePicker
            bundled={bundledPuzzles}
            custom={custom}
            onPlay={play}
            onAddCustom={(added) => {
              const ids = new Set(added.map((p) => p.id));
              updateCustom([...custom.filter((p) => !ids.has(p.id)), ...added]);
            }}
            onRemoveCustom={(id) => {
              updateCustom(custom.filter((p) => p.id !== id));
              // Its saved progress goes with it, or it would sit in storage for good.
              clearProgress(id);
            }}
          />
        }
      />
      <Route path="/p/:shareCode" element={<SharedPuzzle />} />
      <Route path="/local/:id" element={<LocalPuzzle puzzles={[...bundledPuzzles, ...custom]} />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

/** What opening a puzzle from the home screen puts in the history entry. */
interface HistoryState {
  puzzle: PuzzleDef;
  replay?: boolean;
  notice?: string | null;
}

function useHistoryState(): Partial<HistoryState> {
  const state: unknown = useLocation().state;
  return typeof state === 'object' && state !== null ? (state as Partial<HistoryState>) : {};
}

function usePuzzleFromHistory(): PuzzleDef | null {
  return useHistoryState().puzzle ?? null;
}

/**
 * The replay flag and notice the puzzle was opened with, read once. They're then dropped from the
 * history entry, so reloading the page continues the game instead of restarting its clock again.
 */
function useOpenedWith(): { replay: boolean; notice: string | null } {
  const state = useHistoryState();
  const navigate = useNavigate();
  const location = useLocation();
  const [openedWith] = useState(() => ({ replay: state.replay === true, notice: state.notice ?? null }));
  useEffect(() => {
    if (state.replay || state.notice) void navigate(location, { replace: true, state: { puzzle: state.puzzle } });
  }, [state, navigate, location]);
  return openedWith;
}

/** Back to the home screen. */
function useExit(): () => void {
  const navigate = useNavigate();
  return () => void navigate('/');
}

type Loaded = { status: 'loading' } | { status: 'loaded'; puzzle: PuzzleDef } | { status: 'failed'; message: string };

function SharedPuzzle() {
  const { shareCode = '' } = useParams();
  const fromHistory = usePuzzleFromHistory();
  const { replay, notice } = useOpenedWith();
  const exit = useExit();
  const known = fromHistory?.shareCode?.toUpperCase() === shareCode.toUpperCase() ? fromHistory : null;
  const [fetched, setFetched] = useState<Loaded & { code?: string }>({ status: 'loading' });
  const [retries, setRetries] = useState(0);

  useEffect(() => {
    if (known) return;
    const controller = new AbortController();
    fetchByShareCode(shareCode, controller.signal)
      .then((puzzle) => setFetched({ status: 'loaded', puzzle, code: shareCode }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setFetched({ status: 'failed', message: e instanceof Error ? e.message : String(e), code: shareCode });
      });
    return () => controller.abort();
  }, [known, shareCode, retries]);

  const state: Loaded = known ? { status: 'loaded', puzzle: known } : fetched.code === shareCode ? fetched : { status: 'loading' };

  if (state.status === 'loaded') {
    // key resets all game state when switching puzzles.
    return <Game key={state.puzzle.id} puzzle={state.puzzle} replay={replay} notice={notice} onExit={exit} />;
  }
  return (
    <main className="mx-auto grid max-w-xl gap-4 px-4 pt-12">
      {state.status === 'loading' ? (
        <p className="flex items-center gap-2 text-base-content/60" role="status">
          <span className="loading loading-spinner loading-sm" aria-hidden="true" /> Loading puzzle {shareCode.toUpperCase()}…
        </p>
      ) : (
        <div role="alert" className="alert alert-soft alert-warning flex flex-wrap">
          <CircleAlert className="size-5" aria-hidden="true" />
          <span>{state.message}</span>
          <button
            type="button"
            className="btn btn-sm"
            onClick={() => {
              setFetched({ status: 'loading' });
              setRetries((n) => n + 1);
            }}
          >
            <RotateCw className="size-4" aria-hidden="true" /> Retry
          </button>
        </div>
      )}
      <Link to="/" className="link link-primary">
        Go to today's puzzles
      </Link>
    </main>
  );
}

function LocalPuzzle({ puzzles }: { puzzles: PuzzleDef[] }) {
  const { id = '' } = useParams();
  const fromHistory = usePuzzleFromHistory();
  const exit = useExit();
  const puzzle = puzzles.find((p) => p.id === id) ?? (fromHistory?.id === id ? fromHistory : null);
  // Only in another browser's storage, or removed since: nothing to show here.
  if (!puzzle) return <Navigate to="/" replace />;
  return <Game key={puzzle.id} puzzle={puzzle} onExit={exit} />;
}

export default App;
