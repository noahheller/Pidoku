import { CircleAlert, RotateCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router';
import { existingIdToken } from './api/auth';
import { fetchByShareCode, fetchPlayer, fetchPuzzleLeaderboard } from './api/client';
import { sendPendingCompletions } from './api/completions';
import { flushEvents, recordEvent } from './api/events';
import { Game } from './components/Game';
import { LeaderboardPage } from './components/LeaderboardPage';
import { PlayerPage } from './components/PlayerPage';
import { PrivacyPage } from './components/PrivacyPage';
import { currentCosmetics, setSupporter } from './components/cosmetics';
import { AccountPage } from './components/AccountPage';
import { AddFriendPage, FriendsPage } from './components/FriendsPage';
import { LookAndFeelPage } from './components/LookAndFeel';
import { PuzzlePicker, type PlayOptions } from './components/PuzzlePicker';
import { StatsPage } from './components/StatsPage';
import { SupportPage } from './components/Support';
import { Tutorial } from './components/Tutorial';
import { YouPage } from './components/YouPage';
import { timeToBeat, type TimeToBeat } from './game/leaderboardStats';
import type { PuzzleDef } from './game/puzzleFile';
import { hasLockedChoice } from './game/cosmetics';
import { addRecent } from './game/recent';
import { clearProgress, clearResult, hasAccount, loadProgress, loadRecentPuzzles, saveRecentPuzzles } from './game/storage';
import { bundledPuzzles } from './puzzles';

/** Whether this browser had never signed in when the page loaded, before anything here signs it in. */
const firstVisit = !hasAccount();

/**
 * Routes:
 * - `/`: the home screen.
 * - `/p/{shareCode}`: a puzzle from the API. This is the share link, so it works opened cold.
 * - `/local/{id}`: a puzzle that only exists in this browser (bundled, or loaded from a file).
 * - `/leaderboard/{date}?difficulty=hard`: a date's daily leaderboards.
 * - `/friends`: the player's friend code, adding a friend by theirs, and their friends.
 * - `/me`: "You": the player's name, and a menu of the pages below.
 * - `/me/stats`: the player's own stats per difficulty.
 * - `/me/look`: Look & feel (board theme, ink, digits, solve celebration).
 * - `/me/support`: Support Pidoku (a tip on Ko-fi for a supporter badge).
 * - `/me/account`: Account & privacy (Google sign-in, profile visibility, deleting the account).
 * - `/players/{playerId}`: a player's streaks, best times and recent dailies: their own, a
 *   friend's, or a public one. `/friends/{playerId}` is its older address.
 * - `/f/{friendCode}`: a friend's invite link.
 * - `/privacy`: what Pidoku keeps about players, and how to delete it.
 * - `/tutorial`: how to play, a short guided puzzle.
 *
 * Any of them opened with `?ref=share` (share links carry it) records a share-link visit.
 *
 * firebase.json serves index.html for every path, so these all load the app.
 */
function App() {
  const [recent, setRecent] = useState(loadRecentPuzzles);
  const navigate = useNavigate();
  useShareArrival();
  useSupporterCheck();

  // Solves the API couldn't be told about at the time (see api/completions.ts), and analytics
  // events waiting to be sent: try again now, and whenever the connection comes back.
  useEffect(() => {
    const send = () => {
      void sendPendingCompletions();
      void flushEvents();
    };
    send();
    window.addEventListener('online', send);
    return () => window.removeEventListener('online', send);
  }, []);

  // The puzzle rides along in the history entry, so opening it from the home screen doesn't fetch
  // it again. A share link opened cold has no state and fetches it. Every puzzle opened goes on
  // this browser's Recent list; the oldest drop off it, with their saved progress (but a daily's
  // is kept: it's what marks today's cards as solved).
  const play = (puzzle: PuzzleDef, { replay = false, notice = null }: PlayOptions = {}) => {
    const { list, dropped } = addRecent(recent, puzzle);
    setRecent(list);
    saveRecentPuzzles(list);
    for (const old of dropped) {
      if (old.dailyDate) continue;
      clearProgress(old.id);
      clearResult(old.id);
    }
    const path = puzzle.shareCode ? `/p/${puzzle.shareCode}` : `/local/${encodeURIComponent(puzzle.id)}`;
    void navigate(path, { state: { puzzle, replay, notice } satisfies HistoryState });
  };

  return (
    <Routes>
      <Route
        path="/"
        element={
          <PuzzlePicker bundled={bundledPuzzles} recent={recent} onPlay={play} />
        }
      />
      <Route path="/p/:shareCode" element={<SharedPuzzle />} />
      <Route path="/local/:id" element={<LocalPuzzle puzzles={[...bundledPuzzles, ...recent]} />} />
      <Route path="/leaderboard/:date" element={<LeaderboardPage />} />
      <Route path="/friends" element={<FriendsPage />} />
      <Route path="/me" element={<YouPage />} />
      <Route path="/me/stats" element={<StatsPage />} />
      <Route path="/me/look" element={<LookAndFeelPage />} />
      <Route path="/me/support" element={<SupportPage />} />
      <Route path="/me/account" element={<AccountPage />} />
      <Route path="/players/:id" element={<PlayerPage />} />
      <Route path="/friends/:id" element={<PlayerPage />} />
      <Route path="/f/:code" element={<AddFriendPage />} />
      <Route path="/privacy" element={<PrivacyPage />} />
      <Route path="/tutorial" element={<TutorialPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

/** How to play, from the home screen: back there when it's done. */
function TutorialPage() {
  const exit = useExit();
  return <Tutorial onDone={exit} onBack={exit} doneLabel="Play today's puzzles" />;
}

/**
 * Supporter-only looks (game/cosmetics.ts) show from what this browser last heard, so they're
 * there straight away and offline. When some are chosen, asks the API once a visit whether the
 * badge is still there (it isn't for another player signed in here, say). Nobody else is asked:
 * a supporter picks their looks on /me/look, which says so itself.
 */
function useSupporterCheck() {
  useEffect(() => {
    if (!hasLockedChoice(currentCosmetics().chosen) || !hasAccount()) return;
    const controller = new AbortController();
    fetchPlayer(controller.signal)
      .then((player) => setSupporter(player.supporterSince !== null))
      .catch(() => {
        // Offline or the API's down: keep what was known.
      });
    return () => controller.abort();
  }, []);
}

let arrivalRecorded = false;

/**
 * Counts a visit from a share link (`?ref=share`, see shareUrl): whether a share brought someone
 * new is one of the questions analytics answers. The parameter is then dropped from the address,
 * so a reload or a link copied from the address bar doesn't count again.
 */
function useShareArrival() {
  const location = useLocation();
  const navigate = useNavigate();
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get('ref') !== 'share') return;
    if (!arrivalRecorded) {
      arrivalRecorded = true;
      const shareCode = /^\/p\/([^/]+)/.exec(location.pathname)?.[1]?.toUpperCase() ?? null;
      recordEvent('share_arrival', { detail: { shareCode, newVisitor: firstVisit } });
    }
    params.delete('ref');
    const search = params.toString();
    void navigate({ pathname: location.pathname, search: search ? `?${search}` : '', hash: location.hash }, { replace: true, state: location.state as unknown });
  }, [location, navigate]);
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

type Loaded = { status: 'loading' } | { status: 'loaded'; puzzle: PuzzleDef; timeToBeat?: TimeToBeat | null } | { status: 'failed'; message: string };

/**
 * The time to beat for a challenge link: someone else's solve of a shared puzzle that isn't a
 * daily (a daily has its own start screen and leaderboard). Null when there's none, when the
 * player has already started it here, or when the leaderboard can't be fetched: the puzzle then
 * just opens, as it always did.
 */
async function challengeFor(puzzle: PuzzleDef, signal: AbortSignal): Promise<TimeToBeat | null> {
  if (puzzle.dailyDate || !puzzle.shareCode || loadProgress(puzzle.id) !== null) return null;
  const filter = { friendsOnly: false, cleanOnly: false };
  const [everyone, friends] = await Promise.all([
    fetchPuzzleLeaderboard(puzzle.shareCode, filter, signal).catch(() => null),
    // Friends only for a browser that's already signed in: no sign-in just to open a link.
    existingIdToken().then((token) => (token ? fetchPuzzleLeaderboard(puzzle.shareCode!, { ...filter, friendsOnly: true }, signal).catch(() => null) : null)),
  ]);
  return timeToBeat(everyone, friends);
}

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
      .then(async (puzzle) => {
        const toBeat = await challengeFor(puzzle, controller.signal);
        if (!controller.signal.aborted) setFetched({ status: 'loaded', puzzle, timeToBeat: toBeat, code: shareCode });
      })
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setFetched({ status: 'failed', message: e instanceof Error ? e.message : String(e), code: shareCode });
      });
    return () => controller.abort();
  }, [known, shareCode, retries]);

  const state: Loaded = known ? { status: 'loaded', puzzle: known } : fetched.code === shareCode ? fetched : { status: 'loading' };

  if (state.status === 'loaded') {
    // key resets all game state when switching puzzles.
    return <Game key={state.puzzle.id} puzzle={state.puzzle} timeToBeat={state.timeToBeat ?? null} replay={replay} notice={notice} onExit={exit} />;
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
            className="btn btn-plain btn-sm"
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
