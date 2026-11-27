import { Check, ChevronRight, FileText, LogOut, ShieldCheck, Trash2, UserRound } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router';
import { deleteAccount, signOut, type Account } from '../api/auth';
import { deletePlayer, updateVisibility, type Player, type ProfileVisibility } from '../api/client';
import { historyShownTo } from '../game/history';
import { clearAllLocalData } from '../game/storage';
import { reloadCosmetics } from './cosmetics';
import { GoogleButton } from './GoogleButton';
import { Card, LoadStatus, SubPage } from './SubPage';
import { usePlayer } from './usePlayer';
import { VISIBILITY } from './visibility';

/** `/me/account`: Google sign-in, who can see the player's profile and history, their data, and deleting the account. */
export function AccountPage() {
  const { state, reload, setPlayer, afterSignIn, playerChanged } = usePlayer();
  const [deleted, setDeleted] = useState(false);

  return (
    <SubPage back={{ to: '/me', label: 'You' }} icon={ShieldCheck} title="Account & privacy">
      {deleted ? (
        <Deleted />
      ) : (
        <>
          <LoadStatus state={state} onRetry={reload} />
          {state.status === 'loaded' && (
            <>
              {state.account && (
                <Card title="Google sign-in">
                  <AccountSection account={state.account} onSignedIn={afterSignIn} onSignedOut={playerChanged} />
                </Card>
              )}
              <Card title="Who can see your profile">
                <ProfileSettings player={state.player} onSaved={setPlayer} />
              </Card>
              <Card title="Who can see your puzzle history">
                <HistorySettings player={state.player} onSaved={setPlayer} />
              </Card>
              <Card title="Your data">
                <p className="text-sm text-base-content/70">What Pidoku keeps about you, who can see it, and how to delete it.</p>
                <Link to="/privacy" className="btn btn-plain btn-sm w-fit">
                  <FileText className="size-4" aria-hidden="true" /> Privacy: what Pidoku keeps <ChevronRight className="size-4" aria-hidden="true" />
                </Link>
              </Card>
              {state.account && (
                <Card title="Delete account" tone="danger">
                  <DeleteAccount account={state.account} onDeleted={() => setDeleted(true)} />
                </Card>
              )}
            </>
          )}
        </>
      )}
    </SubPage>
  );
}

function AccountSection({ account, onSignedIn, onSignedOut }: { account: Account; onSignedIn: (result: 'linked' | 'switched') => void; onSignedOut: () => void }) {
  const [notice, setNotice] = useState<string | null>(null);

  if (!account.isAnonymous) {
    const out = () => {
      if (!confirm('Sign out? This browser will start over as a new player until you sign in with Google again.')) return;
      void signOut().then(onSignedOut);
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
        className="btn btn-plain w-fit"
        onDone={(result) => {
          if (result === 'linked') setNotice('Done: this player is now your Google account.');
          onSignedIn(result);
        }}
      />
      <Link to="/privacy" className="link link-hover w-fit text-xs text-base-content/60">
        What Pidoku keeps when you sign in
      </Link>
    </div>
  );
}

/** Who can see the player's profile, saved as soon as it's picked, and a link to see it. */
function ProfileSettings({ player, onSaved }: { player: Player; onSaved: (player: Player) => void }) {
  return (
    <div className="grid gap-3">
      <p className="text-sm text-base-content/70">Your profile shows your streaks, best times and this week's dailies.</p>
      <VisibilityChoice
        name="profile-visibility"
        legend="Who can see your profile"
        value={player.profileVisibility}
        save={(profileVisibility) => updateVisibility({ profileVisibility })}
        onSaved={onSaved}
      />
      <Link to={`/players/${player.id}`} className="btn btn-plain btn-sm w-fit">
        <UserRound className="size-4" aria-hidden="true" /> View your profile
      </Link>
    </div>
  );
}

/** Who can see the puzzles the player has solved, on their profile. */
function HistorySettings({ player, onSaved }: { player: Player; onSaved: (player: Player) => void }) {
  const history = player.historyVisibility ?? 'friends';
  const shownTo = historyShownTo(player.profileVisibility, history);
  return (
    <div className="grid gap-3">
      <p className="text-sm text-base-content/70">
        Every puzzle you've solved, random ones included, with your times, so others can open one and try to beat you. Unfinished puzzles never show.
      </p>
      <VisibilityChoice
        name="history-visibility"
        legend="Who can see your puzzle history"
        value={history}
        save={(historyVisibility) => updateVisibility({ historyVisibility })}
        onSaved={onSaved}
      />
      {shownTo !== history && (
        <p className="text-sm text-base-content/70" role="note">
          Your profile is {VISIBILITY[player.profileVisibility].label.toLowerCase()} and your history is shown on it, so {shownTo === 'private' ? 'only you' : 'only your friends'} can see it for now.
        </p>
      )}
    </div>
  );
}

/** Public, friends only or private, saved as soon as it's picked; the choices lock until it is. */
function VisibilityChoice({
  name,
  legend,
  value,
  save,
  onSaved,
}: {
  name: string;
  legend: string;
  value: ProfileVisibility;
  save: (visibility: ProfileVisibility) => Promise<Player>;
  onSaved: (player: Player) => void;
}) {
  const [saving, setSaving] = useState<ProfileVisibility | null>(null);
  const [error, setError] = useState<string | null>(null);
  const shown = saving ?? value;

  const pick = (visibility: ProfileVisibility) => {
    if (visibility === value || saving) return;
    setSaving(visibility);
    setError(null);
    save(visibility)
      .then(onSaved)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)))
      .finally(() => setSaving(null));
  };

  return (
    <>
      <fieldset className="grid gap-2" disabled={saving !== null}>
        <legend className="sr-only">{legend}</legend>
        {(Object.keys(VISIBILITY) as ProfileVisibility[]).map((visibility) => {
          const { label, description, Icon } = VISIBILITY[visibility];
          return (
            <label
              key={visibility}
              className={`flex cursor-pointer items-start gap-3 rounded-box border p-3 transition ${shown === visibility ? 'border-primary bg-primary/5' : 'border-base-300 hover:border-primary/50'}`}
            >
              <input type="radio" name={name} className="radio radio-primary radio-sm mt-0.5" checked={shown === visibility} onChange={() => pick(visibility)} />
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
    </>
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
        reloadCosmetics();
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
