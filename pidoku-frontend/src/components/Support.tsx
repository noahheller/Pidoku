import { Check, Coffee, Copy, ExternalLink, TriangleAlert } from 'lucide-react';
import { useState } from 'react';
import type { Account } from '../api/auth';
import { createSupporterCode, type Player, type SupporterCode } from '../api/client';
import { KOFI_URL } from '../support';

/**
 * The "Support Pidoku" card on /me. A tip on Ko-fi with the player's single-use code in its message
 * gives them a supporter badge on leaderboards and unlocks the supporter looks (LookAndFeel). The code is how the tip finds the player: Ko-fi's
 * details about the payer (name, email) are never kept.
 */
export function SupportSection({ player, account, kofiUrl = KOFI_URL }: { player: Player; account: Account | null; kofiUrl?: string | null }) {
  if (player.supporterSince !== null) {
    return (
      <p className="flex items-center gap-2 text-sm">
        <Coffee className="size-4 shrink-0 text-secondary" aria-hidden="true" />
        <span>
          Supporter since {new Date(player.supporterSince).toLocaleDateString(undefined, { year: 'numeric', month: 'long', day: 'numeric' })}. Thank you! Your badge shows next to your name on
          leaderboards, and every theme, ink, digit style and celebration under Look &amp; feel is yours.
        </span>
      </p>
    );
  }
  if (kofiUrl === null) return null;
  return <SupportSteps kofiUrl={kofiUrl} anonymous={account?.isAnonymous ?? true} />;
}

type CodeState = { status: 'none' } | { status: 'loading' } | { status: 'ready'; code: SupporterCode } | { status: 'failed'; message: string };

function SupportSteps({ kofiUrl, anonymous }: { kofiUrl: string; anonymous: boolean }) {
  const [state, setState] = useState<CodeState>({ status: 'none' });
  const [copied, setCopied] = useState(false);

  const getCode = () => {
    setState({ status: 'loading' });
    setCopied(false);
    createSupporterCode()
      .then((code) => setState({ status: 'ready', code }))
      .catch((e: unknown) => setState({ status: 'failed', message: e instanceof Error ? e.message : String(e) }));
  };
  const copy = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
    } catch {
      // No clipboard access: the code is on screen to copy by hand.
    }
  };

  return (
    <div className="grid gap-3">
      <p className="text-sm text-base-content/70">
        Pidoku is free and has no ads. If you enjoy it, a tip on Ko-fi helps pay for the servers. You get a <Coffee className="inline size-3.5 text-secondary" aria-hidden="true" /> supporter
        badge next to your name on leaderboards, and all the board themes, inks, digit styles and solve celebrations under Look &amp; feel. None of it changes your rank.
      </p>
      <ol className="list-decimal grid gap-1 pl-5 text-sm">
        <li>Get your code (it works once, for 30 days).</li>
        <li>Paste it into the message of your tip on Ko-fi.</li>
        <li>Your badge and the rest appear once Ko-fi tells us about the tip, usually within a minute. Reload this page to see them.</li>
      </ol>
      {anonymous && (
        <p className="flex items-start gap-2 text-sm text-warning">
          <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>You're playing without a Google account, so clearing this browser's data loses your badge. Sign in with Google below first to keep it.</span>
        </p>
      )}

      {state.status === 'ready' ? (
        <div className="grid gap-2 rounded-box bg-base-200 p-3">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xl font-bold tracking-wider select-all" aria-label={`Your supporter code: ${state.code.code.split('').join(' ')}`}>
              {state.code.code}
            </span>
            <button type="button" className="btn btn-sm ml-auto" onClick={() => copy(state.code.code)}>
              {copied ? <Check className="size-4" aria-hidden="true" /> : <Copy className="size-4" aria-hidden="true" />}
              {copied ? 'Copied' : 'Copy code'}
            </button>
          </div>
          <p className="text-xs text-base-content/60">Only the code links your tip to you: Pidoku keeps nothing Ko-fi knows about you, such as your name or email.</p>
        </div>
      ) : (
        <button type="button" className="btn btn-sm w-fit" onClick={getCode} disabled={state.status === 'loading'}>
          {state.status === 'loading' && <span className="loading loading-spinner loading-xs" aria-hidden="true" />}
          Get my supporter code
        </button>
      )}
      {state.status === 'failed' && (
        <p role="alert" className="text-sm text-error">
          {state.message}
        </p>
      )}

      <a href={kofiUrl} target="_blank" rel="noopener noreferrer" className="btn btn-sm btn-secondary w-fit">
        <Coffee className="size-4" aria-hidden="true" /> Tip on Ko-fi <ExternalLink className="size-3.5" aria-hidden="true" />
      </a>
    </div>
  );
}
