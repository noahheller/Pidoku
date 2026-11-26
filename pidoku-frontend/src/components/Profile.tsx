import { ArrowLeft, Check, ChevronRight, CircleAlert, Copy, Flame, LogOut, RotateCw, Share2, ShieldCheck, Trash2, UserPlus, UserRound, Users, X } from 'lucide-react';
import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { currentAccount, deleteAccount, signInWithGoogle, signOut, SignInError, type Account } from '../api/auth';
import { addFriend, ApiError, deletePlayer, fetchFriends, fetchPlayer, removeFriend, updateDisplayName, updateProfileVisibility, type Friend, type Player, type ProfileVisibility } from '../api/client';
import { sendPendingCompletions } from '../api/completions';
import { friendUrl } from '../game/daily';
import { clearAllLocalData, clearPlayerCaches } from '../game/storage';
import { VISIBILITY } from './visibility';

type Loaded =
  | { status: 'loading' }
  | { status: 'loaded'; account: Account | null; player: Player; friends: Friend[] }
  | { status: 'failed'; message: string }
  | { status: 'deleted' };

/**
 * `/me`: the player's name, profile, friends and account. Signs the player in (anonymously) when opened,
 * like starting a puzzle does.
 */
export function Profile() {
  const [state, setState] = useState<Loaded>({ status: 'loading' });
  const [reloads, setReloads] = useState(0);
  const reload = useCallback(() => {
    setState({ status: 'loading' });
    setReloads((n) => n + 1);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    Promise.all([currentAccount(), fetchPlayer(controller.signal), fetchFriends(controller.signal)])
      .then(([account, player, friends]) => setState({ status: 'loaded', account, player, friends }))
      .catch((e: unknown) => {
        if (!controller.signal.aborted) setState({ status: 'failed', message: e instanceof Error ? e.message : String(e) });
      });
    return () => controller.abort();
  }, [reloads]);

  return (
    <main className="mx-auto grid max-w-xl gap-5 px-4 pt-4 pb-16">
      <header className="grid gap-3">
        <Link to="/" className="btn btn-ghost btn-sm w-fit text-primary">
          <ArrowLeft className="size-4 shrink-0" aria-hidden="true" /> Puzzles
        </Link>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <Users className="size-6 text-primary" aria-hidden="true" /> You &amp; friends
        </h1>
      </header>

      {state.status === 'loading' && (
        <p className="flex items-center gap-2 text-base-content/60" role="status">
          <span className="loading loading-spinner loading-sm" aria-hidden="true" /> Loading…
        </p>
      )}
      {state.status === 'failed' && <Failed message={state.message} onRetry={reload} />}
      {state.status === 'deleted' && <Deleted />}
      {state.status === 'loaded' && (
        <>
          <Card title="Your name">
            {/* Keyed by the name, so a name set elsewhere (from Google, on sign-in) fills the field. */}
            <NameForm key={state.player.displayName ?? ''} player={state.player} onSaved={(player) => setState({ ...state, player })} />
          </Card>
          <Card title="Your profile">
            <ProfileSettings player={state.player} onSaved={(player) => setState({ ...state, player })} />
          </Card>
          <Card title="Friends">
            <FriendCode code={state.player.friendCode} />
            <AddFriendForm onAdded={(friend) => setState({ ...state, friends: mergeFriend(state.friends, friend) })} />
            <FriendList friends={state.friends} onRemoved={(id) => setState({ ...state, friends: state.friends.filter((f) => f.id !== id) })} />
          </Card>
          {state.account && (
            <Card title="Account">
              <AccountSection
                account={state.account}
                onLinked={() => {
                  setState({ ...state, account: { ...state.account!, isAnonymous: false } });
                  // A player with no name takes their Google account's on the next request: show it.
                  fetchPlayer()
                    .then((player) => setState((s) => (s.status === 'loaded' ? { ...s, player } : s)))
                    .catch(() => {
                      // The old name stays on screen until the page is opened again.
                    });
                }}
                onPlayerChanged={() => {
                  // Another player now: what was cached for the old one no longer applies.
                  clearPlayerCaches();
                  reload();
                }}
              />
            </Card>
          )}
          {state.account && (
            <Card title="Delete account">
              <DeleteAccount account={state.account} onDeleted={() => setState({ status: 'deleted' })} />
            </Card>
          )}
        </>
      )}
      <Link to="/privacy" className="link link-hover w-fit text-sm text-base-content/60">
        Privacy: what Pidoku keeps about you
      </Link>
    </main>
  );
}

function mergeFriend(friends: Friend[], friend: Friend): Friend[] {
  return friends.some((f) => f.id === friend.id) ? friends : [...friends, friend];
}

function Card({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="card border border-base-300 bg-base-100 shadow-sm">
      <div className="card-body gap-4 p-4 sm:p-5">
        <h2 className="text-lg font-semibold">{title}</h2>
        {children}
      </div>
    </section>
  );
}

function Failed({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="alert alert-soft alert-warning flex flex-wrap">
      <CircleAlert className="size-5" aria-hidden="true" />
      <span>{message}</span>
      <button type="button" className="btn btn-sm" onClick={onRetry}>
        <RotateCw className="size-4" aria-hidden="true" /> Retry
      </button>
    </div>
  );
}

function NameForm({ player, onSaved }: { player: Player; onSaved: (player: Player) => void }) {
  const [name, setName] = useState(player.displayName ?? '');
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);
  const unchanged = name.trim() === (player.displayName ?? '');

  const save = (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setMessage(null);
    updateDisplayName(name)
      .then((saved) => {
        onSaved(saved);
        setName(saved.displayName ?? '');
        setMessage({ ok: true, text: 'Saved. Friends and leaderboards show this name.' });
      })
      .catch((err: unknown) => setMessage({ ok: false, text: err instanceof Error ? err.message : String(err) }))
      .finally(() => setSaving(false));
  };

  return (
    <form className="grid gap-2" onSubmit={save}>
      <label htmlFor="profile-name" className="text-sm text-base-content/70">
        What friends and leaderboards show. {player.displayName === null && "Until you pick one, you show as Anonymous (or your Google account's name, once you sign in with Google)."}
      </label>
      <div className="flex gap-2">
        {/* text-base (16px) so iOS doesn't zoom in when the field is focused. */}
        <input
          id="profile-name"
          className="input min-w-0 flex-1 text-base"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={60}
          autoComplete="nickname"
          placeholder="Your name"
        />
        <button type="submit" className="btn btn-primary" disabled={saving || unchanged || !name.trim()}>
          {saving && <span className="loading loading-spinner loading-xs" aria-hidden="true" />} Save
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

/** A link to the player's own profile, and who else can see it. Saved as soon as it's picked. */
function ProfileSettings({ player, onSaved }: { player: Player; onSaved: (player: Player) => void }) {
  const [saving, setSaving] = useState<ProfileVisibility | null>(null);
  const [error, setError] = useState<string | null>(null);
  const shown = saving ?? player.profileVisibility;

  const pick = (visibility: ProfileVisibility) => {
    if (visibility === player.profileVisibility || saving) return;
    setSaving(visibility);
    setError(null);
    updateProfileVisibility(visibility)
      .then(onSaved)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)))
      .finally(() => setSaving(null));
  };

  return (
    <div className="grid gap-3">
      <p className="text-sm text-base-content/70">Your streaks, best times and this week's dailies.</p>
      <Link to={`/players/${player.id}`} className="btn btn-sm w-fit">
        <UserRound className="size-4" aria-hidden="true" /> View your profile
      </Link>
      <fieldset className="grid gap-2" disabled={saving !== null}>
        <legend className="mb-2 text-sm font-semibold">Who can see it</legend>
        {(Object.keys(VISIBILITY) as ProfileVisibility[]).map((visibility) => {
          const { label, description, Icon } = VISIBILITY[visibility];
          return (
            <label
              key={visibility}
              className={`flex cursor-pointer items-start gap-3 rounded-box border p-3 transition ${shown === visibility ? 'border-primary bg-primary/5' : 'border-base-300 hover:border-primary/50'}`}
            >
              <input
                type="radio"
                name="profile-visibility"
                className="radio radio-primary radio-sm mt-0.5"
                checked={shown === visibility}
                onChange={() => pick(visibility)}
              />
              <span className="grid gap-0.5">
                <span className="flex items-center gap-1.5 font-medium">
                  <Icon className="size-4" aria-hidden="true" /> {label}
                  {saving === visibility && <span className="loading loading-spinner loading-xs" aria-hidden="true" />}
                </span>
                <span className="text-sm text-base-content/70">{description}</span>
              </span>
            </label>
          );
        })}
      </fieldset>
      {error && (
        <p className="text-sm text-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
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
    <div className="grid gap-2 rounded-box bg-base-200 p-3">
      <p className="text-sm text-base-content/70">Send friends your link, or have them enter your code below theirs.</p>
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-2xl font-bold tracking-widest select-all" aria-label={`Your friend code: ${code.split('').join(' ')}`}>
          {code}
        </span>
        <div className="ml-auto flex gap-2">
          <button type="button" className="btn btn-sm" onClick={copy}>
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
        <button type="submit" className="btn" disabled={adding || !code.trim()}>
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
  if (friends.length === 0) return <p className="text-sm text-base-content/60">No friends yet. Their times will show on the Friends leaderboard.</p>;

  const remove = (friend: Friend) => {
    if (!confirm(`Remove ${friend.displayName ?? 'this friend'}? You'll each drop off the other's Friends leaderboard.`)) return;
    setError(null);
    removeFriend(friend.id)
      .then(() => onRemoved(friend.id))
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)));
  };

  return (
    <div className="grid gap-1">
      <ul className="grid gap-1" aria-label="Your friends">
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

/** Google sign-in, so the account isn't lost with this browser's data and works on other devices. */
export function GoogleButton({ onDone, className = 'btn' }: { onDone: (result: 'linked' | 'switched') => void; className?: string }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const signIn = () => {
    setBusy(true);
    setError(null);
    // Unsent solves belong to this browser's account, so send them before switching away from it.
    signInWithGoogle(() => sendPendingCompletions())
      .then((result) => {
        if (result !== 'cancelled') onDone(result);
      })
      .catch((e: unknown) => setError(e instanceof SignInError ? e.message : String(e)))
      .finally(() => setBusy(false));
  };

  return (
    <div className="grid gap-2">
      <button type="button" className={className} onClick={signIn} disabled={busy}>
        {busy ? <span className="loading loading-spinner loading-xs" aria-hidden="true" /> : <GoogleIcon />} Sign in with Google
      </button>
      {error && (
        <p className="text-sm text-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

function AccountSection({ account, onLinked, onPlayerChanged }: { account: Account; onLinked: () => void; onPlayerChanged: () => void }) {
  const [notice, setNotice] = useState<string | null>(null);

  if (!account.isAnonymous) {
    const out = () => {
      if (!confirm('Sign out? This browser will start over as a new player until you sign in with Google again.')) return;
      void signOut().then(onPlayerChanged);
    };
    return (
      <div className="grid gap-3">
        <p className="flex items-center gap-2 text-sm">
          <ShieldCheck className="size-5 shrink-0 text-success" aria-hidden="true" />
          <span>
            Signed in with Google{account.email && <> as <strong>{account.email}</strong></>}. Your streak and times are safe, and you can play on any device.
          </span>
        </p>
        {notice && (
          <p className="text-sm text-success" role="status">
            {notice}
          </p>
        )}
        <button type="button" className="btn btn-ghost btn-sm w-fit" onClick={out}>
          <LogOut className="size-4" aria-hidden="true" /> Sign out
        </button>
      </div>
    );
  }

  return (
    <div className="grid gap-3">
      <p className="text-sm text-base-content/70">
        You're playing without an account, so your streak and times live in this browser only. Clearing its data loses them. Sign in with Google to keep them and play on other devices too.
      </p>
      <GoogleButton
        className="btn w-fit"
        onDone={(result) => {
          if (result === 'linked') {
            setNotice('Done: this player is now your Google account.');
            onLinked();
          } else {
            // The Google account was already a player elsewhere: this browser uses that one now.
            onPlayerChanged();
          }
        }}
      />
      <Link to="/privacy" className="link link-hover w-fit text-xs text-base-content/60">
        What Pidoku keeps when you sign in
      </Link>
    </div>
  );
}

/** Deletes the player for good, after they confirm on the page (not a dialog, which is easy to click through). */
function DeleteAccount({ account, onDeleted }: { account: Account; onDeleted: () => void }) {
  const [step, setStep] = useState<'idle' | 'confirming' | 'deleting'>('idle');
  const [error, setError] = useState<string | null>(null);

  const remove = () => {
    setStep('deleting');
    setError(null);
    deleteAccount(deletePlayer)
      .then((result) => {
        if (result === 'cancelled') {
          setStep('confirming');
          return;
        }
        clearAllLocalData();
        onDeleted();
      })
      .catch((e: unknown) => {
        setError(e instanceof Error ? e.message : String(e));
        setStep('confirming');
      });
  };

  if (step === 'idle') {
    return (
      <div className="grid gap-3">
        <p className="text-sm text-base-content/70">Delete your name, friends, streak and every solve, from the server and from this browser.</p>
        <button type="button" className="btn btn-sm btn-outline btn-error w-fit" onClick={() => setStep('confirming')}>
          <Trash2 className="size-4" aria-hidden="true" /> Delete my account
        </button>
      </div>
    );
  }

  return (
    <div role="alert" className="grid gap-3 rounded-box border border-error/40 bg-error/5 p-3">
      <p className="text-sm font-semibold">This can't be undone. Deleting your account removes:</p>
      <ul className="list-disc ps-5 text-sm text-base-content/80">
        <li>your name and friend code, and you from your friends' lists</li>
        <li>every solve and your streak, so your times leave all leaderboards</li>
        <li>your {account.isAnonymous ? 'account' : 'Google sign-in to Pidoku'}, and Pidoku's data in this browser (progress, results, your puzzles)</li>
      </ul>
      {!account.isAnonymous && <p className="text-sm text-base-content/70">Google will ask you to confirm it's you first.</p>}
      <div className="flex flex-wrap gap-2">
        <button type="button" className="btn btn-sm btn-error" onClick={remove} disabled={step === 'deleting'}>
          {step === 'deleting' ? <span className="loading loading-spinner loading-xs" aria-hidden="true" /> : <Trash2 className="size-4" aria-hidden="true" />} Delete forever
        </button>
        <button type="button" className="btn btn-sm btn-ghost" onClick={() => setStep('idle')} disabled={step === 'deleting'}>
          Cancel
        </button>
      </div>
      {error && <p className="text-sm text-error">{error}</p>}
    </div>
  );
}

function Deleted() {
  return (
    <section className="card border border-base-300 bg-base-100 shadow-sm">
      <div className="card-body items-start gap-3 p-4 sm:p-5">
        <h2 className="flex items-center gap-2 text-lg font-semibold">
          <Check className="size-5 text-success" aria-hidden="true" /> Your account is deleted
        </h2>
        <p className="text-sm text-base-content/80">Your name, friends, solves and streak are gone, and this browser has forgotten Pidoku's data. You can still play: you'll start over as a new player.</p>
        {/* A full reload, so nothing from the deleted player stays in memory. */}
        <a href="/" className="btn btn-primary btn-sm">
          Back to puzzles
        </a>
      </div>
    </section>
  );
}

function GoogleIcon() {
  return (
    <svg className="size-4" viewBox="0 0 48 48" aria-hidden="true">
      <path fill="#FFC107" d="M43.6 20.1H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.6-.4-3.9z" />
      <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
      <path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z" />
      <path fill="#1976D2" d="M43.6 20.1H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.6-.4-3.9z" />
    </svg>
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
      <Link to="/me" className="link link-primary text-center">
        Your name and friends
      </Link>
    </main>
  );
}
