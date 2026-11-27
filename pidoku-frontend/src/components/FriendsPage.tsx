import { Check, ChevronRight, Copy, Flame, Share2, UserPlus, UserRound, Users, X } from 'lucide-react';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { addFriend, ApiError, fetchFriends, fetchPlayer, removeFriend, type Friend, type Player } from '../api/client';
import { friendUrl } from '../game/daily';
import { Card, LoadStatus, SubPage } from './SubPage';

type Loaded = { status: 'loading' } | { status: 'loaded'; player: Player; friends: Friend[] } | { status: 'failed'; message: string };

/**
 * `/friends`: the player's friend code to send, adding a friend by theirs, and the friends they
 * have. Signs the player in (anonymously) when opened, like starting a puzzle does.
 */
export function FriendsPage() {
  const [state, setState] = useState<Loaded>({ status: 'loading' });
  const [reloads, setReloads] = useState(0);
  const reload = useCallback(() => {
    setState({ status: 'loading' });
    setReloads((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([fetchPlayer(controller.signal), fetchFriends(controller.signal)])
      .then(([player, friends]) => setState({ status: 'loaded', player, friends }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ status: 'failed', message: e instanceof Error ? e.message : String(e) });
      });
    return () => controller.abort();
  }, [reloads]);

  return (
    <SubPage back={{ to: '/', label: 'Puzzles' }} icon={Users} title="Friends">
      <LoadStatus state={state} onRetry={reload} />
      {state.status === 'loaded' && (
        <>
          {state.player.displayName === null && (
            <div role="note" className="alert border-base-300 bg-base-100 text-sm">
              <UserRound className="size-5 text-base-content/60" aria-hidden="true" />
              <span>
                Friends see you as Anonymous.{' '}
                <Link to="/me" className="link link-primary">
                  Pick a name
                </Link>
              </span>
            </div>
          )}
          <Card title="Invite friends">
            <FriendCode code={state.player.friendCode} />
          </Card>
          <Card
            title={
              <>
                Your friends
                {state.friends.length > 0 && <span className="badge badge-sm badge-ghost tabular-nums">{state.friends.length}</span>}
              </>
            }
          >
            <AddFriendForm onAdded={(friend) => setState({ ...state, friends: mergeFriend(state.friends, friend) })} />
            <FriendList friends={state.friends} onRemoved={(id) => setState({ ...state, friends: state.friends.filter((f) => f.id !== id) })} />
          </Card>
        </>
      )}
    </SubPage>
  );
}

function mergeFriend(friends: Friend[], friend: Friend): Friend[] {
  return friends.some((f) => f.id === friend.id) ? friends : [...friends, friend];
}

function FriendCode({ code }: { code: string }) {
  const [copied, setCopied] = useState(false);
  const url = friendUrl(code);
  const canShare = typeof navigator.share === 'function';

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
    } catch {
      // No clipboard access: the code is on screen to copy by hand.
    }
  };
  const share = () => {
    navigator.share({ title: 'Pidoku', text: 'Add me as a friend on Pidoku and compare times on the daily puzzles:', url }).catch(() => {
      // Cancelled, or not allowed: nothing to do.
    });
  };

  return (
    <div className="grid gap-2">
      <p className="text-sm text-base-content/70">Send friends your link, or have them add your code on their Friends page. You'll see each other's times on the Friends leaderboard.</p>
      <div className="flex flex-wrap items-center gap-2 rounded-box bg-base-200 p-3">
        <span className="font-mono text-2xl font-bold tracking-widest select-all" aria-label={`Your friend code: ${code.split('').join(' ')}`}>
          {code}
        </span>
        <div className="ml-auto flex gap-2">
          <button type="button" className="btn btn-plain btn-sm" onClick={copy}>
            {copied ? <Check className="size-4" aria-hidden="true" /> : <Copy className="size-4" aria-hidden="true" />}
            {copied ? 'Link copied' : 'Copy link'}
          </button>
          {canShare && (
            <button type="button" className="btn btn-sm btn-primary" onClick={share}>
              <Share2 className="size-4" aria-hidden="true" /> Share
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function AddFriendForm({ onAdded }: { onAdded: (friend: Friend) => void }) {
  const [code, setCode] = useState('');
  const [adding, setAdding] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const add = (e: FormEvent) => {
    e.preventDefault();
    setAdding(true);
    setMessage(null);
    addFriend(code)
      .then((friend) => {
        onAdded(friend);
        setCode('');
        setMessage({ ok: true, text: `${friend.displayName ?? 'Anonymous'} is now your friend.` });
      })
      .catch((err: unknown) => setMessage({ ok: false, text: err instanceof Error ? err.message : String(err) }))
      .finally(() => setAdding(false));
  };

  return (
    <form className="grid gap-2" onSubmit={add}>
      <label htmlFor="friend-code" className="text-sm font-semibold">
        Add a friend by code
      </label>
      <div className="flex gap-2">
        {/* text-base (16px) so iOS doesn't zoom in when the field is focused. */}
        <input
          id="friend-code"
          className="input min-w-0 flex-1 font-mono text-base uppercase placeholder:font-sans placeholder:normal-case"
          value={code}
          onChange={(e) => setCode(e.target.value)}
          maxLength={12}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          placeholder="e.g. 7KQ4M2XD"
        />
        <button type="submit" className="btn btn-plain" disabled={adding || !code.trim()}>
          {adding ? <span className="loading loading-spinner loading-xs" aria-hidden="true" /> : <UserPlus className="size-4" aria-hidden="true" />} Add
        </button>
      </div>
      {message && (
        <p className={`text-sm ${message.ok ? 'text-success' : 'text-error'}`} role={message.ok ? 'status' : 'alert'}>
          {message.text}
        </p>
      )}
    </form>
  );
}

function FriendList({ friends, onRemoved }: { friends: Friend[]; onRemoved: (id: number) => void }) {
  const [error, setError] = useState<string | null>(null);
  if (friends.length === 0) return <p className="text-sm text-base-content/60">No friends yet. Send them your link above, and their times will show on the Friends leaderboard.</p>;

  const remove = (friend: Friend) => {
    if (!confirm(`Remove ${friend.displayName ?? 'this friend'}? You'll each drop off the other's Friends leaderboard.`)) return;
    setError(null);
    removeFriend(friend.id)
      .then(() => onRemoved(friend.id))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)));
  };

  return (
    <div className="grid gap-1">
      <ul className="grid gap-1 border-t border-base-300 pt-3" aria-label="Your friends">
        {friends.map((friend) => (
          <li key={friend.id} className="flex items-center gap-2 rounded-field px-2 py-1.5 hover:bg-base-200">
            <Link to={`/players/${friend.id}`} className="flex min-w-0 flex-1 items-center gap-2 rounded-field py-0.5 focus-visible:outline-2 focus-visible:outline-primary">
              <span className="min-w-0 flex-1 truncate">{friend.displayName ?? <span className="text-base-content/60 italic">Anonymous</span>}</span>
              {friend.currentStreak !== null && friend.currentStreak > 0 && (
                <span className="flex items-center gap-0.5 text-sm font-semibold text-warning tabular-nums" title={`${friend.currentStreak}-day streak`}>
                  <Flame className="size-4" aria-hidden="true" /> {friend.currentStreak}
                  <span className="sr-only">-day streak</span>
                </span>
              )}
              <ChevronRight className="size-4 shrink-0 text-base-content/40" aria-hidden="true" />
            </Link>
            <button
              type="button"
              className="btn btn-circle btn-ghost btn-xs text-base-content/60 hover:bg-error/10 hover:text-error"
              aria-label={`Remove ${friend.displayName ?? 'friend'}`}
              onClick={() => remove(friend)}
            >
              <X className="size-4" aria-hidden="true" />
            </button>
          </li>
        ))}
      </ul>
      {error && (
        <p className="text-sm text-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

type Adding = { status: 'ready' } | { status: 'adding' } | { status: 'added'; friend: Friend } | { status: 'failed'; message: string };

/** `/f/{code}`: a friend's invite link. Adding them signs the player in, like starting a puzzle. */
export function AddFriendPage() {
  const { code = '' } = useParams();
  const [state, setState] = useState<Adding>({ status: 'ready' });
  const shown = code.toUpperCase();

  const add = () => {
    setState({ status: 'adding' });
    addFriend(code)
      .then((friend) => setState({ status: 'added', friend }))
      .catch((e: unknown) => setState({ status: 'failed', message: e instanceof ApiError ? e.message : String(e) }));
  };

  return (
    <main className="mx-auto grid max-w-md gap-4 px-4 pt-12">
      <section className="card border border-base-300 bg-base-100 shadow-sm">
        <div className="card-body items-center gap-4 p-6 text-center sm:p-8">
          <UserPlus className="size-10 text-primary" aria-hidden="true" />
          {state.status === 'added' ? (
            <>
              <h1 className="text-2xl font-bold">You're friends!</h1>
              <p className="text-base-content/80">
                You and {state.friend.displayName ?? 'your friend'} can now see each other's times on the Friends leaderboard.
              </p>
              <Link to="/" className="btn btn-primary w-full">
                Play today's puzzles
              </Link>
            </>
          ) : (
            <>
              <h1 className="text-2xl font-bold">Add a friend</h1>
              <p className="text-base-content/80">
                Someone sent you their Pidoku friend code <span className="font-mono font-bold tracking-wider">{shown}</span>. Add them to compare times on the daily puzzles.
              </p>
              <button type="button" className="btn btn-primary btn-lg w-full" onClick={add} disabled={state.status === 'adding'}>
                {state.status === 'adding' ? <span className="loading loading-spinner loading-sm" aria-hidden="true" /> : <UserPlus className="size-5" aria-hidden="true" />} Add friend
              </button>
              {state.status === 'failed' && (
                <p className="text-sm text-error" role="alert">
                  {state.message}
                </p>
              )}
            </>
          )}
        </div>
      </section>
      <Link to="/friends" className="link link-primary text-center">
        Your friends
      </Link>
    </main>
  );
}
