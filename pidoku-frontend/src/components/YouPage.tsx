import { BarChart3, ChevronRight, CircleCheck, Coffee, IdCard, Palette, ShieldCheck, UserRound, type LucideIcon } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { errorMessage, updateDisplayName, type Player } from '../api/client';
import { KOFI_URL } from '../support';
import { GoogleButton } from './GoogleButton';
import { Card, LoadStatus, SubPage } from './SubPage';
import { usePlayer } from './usePlayer';
import { VISIBILITY } from './visibility';

/**
 * `/me`: the player's name, a nudge to sign in with Google while they're a guest, and a menu of
 * the pages about them: stats, profile, looks, support, and account and privacy.
 */
export function YouPage() {
  const { state, reload, setPlayer, afterSignIn } = usePlayer();
  const [linked, setLinked] = useState(false);

  return (
    <SubPage back={{ to: '/', label: 'Puzzles' }} icon={UserRound} title="You">
      <LoadStatus state={state} onRetry={reload} />
      {state.status === 'loaded' && (
        <>
          <Card title="Your name">
            {/* Keyed by the name, so a name set elsewhere (from Google, on sign-in) fills the field. */}
            <NameForm key={state.player.displayName ?? ''} player={state.player} onSaved={setPlayer} />
          </Card>

          {linked && (
            <div role="status" className="alert alert-soft alert-success">
              <CircleCheck className="size-5" aria-hidden="true" />
              <span>Signed in. Your streak and times are kept with your Google account now.</span>
            </div>
          )}
          {state.account?.isAnonymous && (
            <section aria-labelledby="guest-title" className="card border border-primary/30 bg-primary/5">
              <div className="card-body gap-3 p-4 sm:p-5">
                <h2 id="guest-title" className="text-lg font-semibold">
                  Keep your progress
                </h2>
                <p className="text-sm text-base-content/70">
                  Your streak and times live only in this browser, so clearing its data loses them. Sign in with Google to keep them and play on other devices too.
                </p>
                <GoogleButton
                  className="btn btn-plain w-fit"
                  onDone={(result) => {
                    if (result === 'linked') setLinked(true);
                    afterSignIn(result);
                  }}
                />
              </div>
            </section>
          )}

          <nav aria-label="You">
            <ul className="list rounded-box border border-base-300 bg-base-100 p-1 shadow-sm">
              <MenuRow to="/me/stats" icon={BarChart3} title="Your stats" detail="Times and clean rate at each difficulty. Only you see them." />
              <MenuRow to={`/players/${state.player.id}`} icon={IdCard} title="Your profile" detail={`What others see · ${VISIBILITY[state.player.profileVisibility].label}`} />
              <MenuRow to="/me/look" icon={Palette} title="Look & feel" detail="Board theme, ink, digits and solve celebration" />
              {(state.player.supporterSince !== null || KOFI_URL !== null) && (
                <MenuRow
                  to="/me/support"
                  icon={Coffee}
                  title="Support Pidoku"
                  detail={state.player.supporterSince !== null ? `Supporter since ${formatDay(state.player.supporterSince)}. Thank you!` : 'Tip on Ko-fi for a badge and more looks'}
                />
              )}
              <MenuRow
                to="/me/account"
                icon={ShieldCheck}
                title="Account & privacy"
                detail={
                  state.account && !state.account.isAnonymous
                    ? `Signed in with Google${state.account.email ? ` as ${state.account.email}` : ''}`
                    : 'Guest on this browser · sign in, delete account'
                }
              />
            </ul>
          </nav>
        </>
      )}
    </SubPage>
  );
}

function formatDay(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' });
}

/** One row of the You menu: an icon, a title, a line about what's there, and a chevron. */
function MenuRow({ to, icon: Icon, title, detail }: { to: string; icon: LucideIcon; title: string; detail: string }) {
  return (
    <li>
      <Link
        to={to}
        className="list-row items-center gap-3 px-3 py-3 transition-colors hover:bg-base-200 focus-visible:bg-base-200 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
      >
        <span className="grid size-10 place-items-center rounded-field bg-primary/10 text-primary">
          <Icon className="size-5" aria-hidden="true" />
        </span>
        <span className="grid min-w-0 gap-0.5">
          <span className="text-base font-semibold">{title}</span>
          <span className="text-sm text-base-content/60">{detail}</span>
        </span>
        <ChevronRight className="size-5 text-base-content/40" aria-hidden="true" />
      </Link>
    </li>
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
      .catch((err: unknown) => setMessage({ ok: false, text: errorMessage(err) }))
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
