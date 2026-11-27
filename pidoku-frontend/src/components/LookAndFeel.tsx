import { Coffee, Lock, Palette, Play } from 'lucide-react';
import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import {
  CELEBRATIONS,
  DIGIT_STYLES,
  INKS,
  inkColor,
  THEMES,
  type CelebrationId,
  type CosmeticOption,
  type Cosmetics,
  type DigitStyleId,
  type InkOption,
  type ThemeOption,
} from '../game/cosmetics';
import { KOFI_URL } from '../support';
import { Celebration } from './Celebration';
import { chooseCosmetic, loadMonoFont, useCosmetics } from './cosmetics';
import { Card, Loading, SubPage } from './SubPage';
import { usePlayer } from './usePlayer';

/**
 * `/me/look`. It waits for the player, so a new supporter's badge is known (see usePlayer) before
 * they pick a look. If the API can't be reached, the options still work from what was known.
 */
export function LookAndFeelPage() {
  const { state } = usePlayer();
  return (
    <SubPage back={{ to: '/me', label: 'You' }} icon={Palette} title="Look & feel">
      {state.status === 'loading' ? (
        <Loading />
      ) : (
        <Card>
          <LookAndFeel canSupport={KOFI_URL !== null} />
        </Card>
      )}
    </SubPage>
  );
}

/**
 * Board theme, ink colour, digit style and solve celebration. Everyone can see every option.
 * The supporter-only ones are a thank-you for a tip on Ko-fi: picking one without the badge says
 * so, and points at Support Pidoku (`/me/support`) when `canSupport`.
 */
export function LookAndFeel({ canSupport }: { canSupport: boolean }) {
  const { active, supporter } = useCosmetics();
  const [locked, setLocked] = useState<{ group: keyof Cosmetics; label: string } | null>(null);
  const [preview, setPreview] = useState<{ kind: CelebrationId; run: number } | null>(null);
  const endPreview = useCallback(() => setPreview(null), []);

  // The mono digits' preview needs its font; it's only fetched here and when chosen.
  useEffect(loadMonoFont, []);

  const pick = <K extends keyof Cosmetics>(group: K, option: CosmeticOption<Cosmetics[K]>) => {
    if (option.supporterOnly && !supporter) {
      setLocked({ group, label: option.label });
      return;
    }
    setLocked(null);
    chooseCosmetic(group, option.id);
  };
  const isLocked = (option: CosmeticOption<string>) => option.supporterOnly && !supporter;
  const lockedNote = (group: keyof Cosmetics) => locked?.group === group && <LockedNote label={locked.label} canSupport={canSupport} />;

  return (
    <div className="grid gap-5">
      <p className="text-sm text-base-content/70">
        {supporter ? (
          <>Thanks for supporting Pidoku! Everything here is yours to use. Only you see these: they never change your rank.</>
        ) : (
          <>
            Options with a <Lock className="inline size-3.5" aria-hidden="true" /> are a thank-you for supporters on Ko-fi. Only you see them, and they never change your rank.
          </>
        )}
      </p>

      <Group title="Board theme">
        <div className="grid grid-cols-3 gap-2">
          {THEMES.map((theme) => (
            <OptionButton key={theme.id} label={theme.label} selected={active.theme === theme.id} locked={isLocked(theme)} onClick={() => pick('theme', theme)}>
              <ThemeSwatch theme={theme} />
            </OptionButton>
          ))}
        </div>
        {lockedNote('theme')}
      </Group>

      <Group title="Ink">
        <div className="flex flex-wrap gap-2">
          {INKS.map((ink) => (
            <OptionButton key={ink.id} label={ink.label} selected={active.ink === ink.id} locked={isLocked(ink)} onClick={() => pick('ink', ink)}>
              <InkSwatch ink={ink} />
            </OptionButton>
          ))}
        </div>
        {lockedNote('ink')}
      </Group>

      <Group title="Digits">
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {DIGIT_STYLES.map((style) => (
            <OptionButton key={style.id} label={style.label} selected={active.digitStyle === style.id} locked={isLocked(style)} onClick={() => pick('digitStyle', style)}>
              <DigitSample style={style.id} />
            </OptionButton>
          ))}
        </div>
        {lockedNote('digitStyle')}
      </Group>

      <Group title="When you solve a puzzle">
        <div className="flex flex-wrap items-start gap-4">
          <div className="grid flex-1 grid-cols-2 gap-2">
            {CELEBRATIONS.map((celebration) => (
              <OptionButton
                key={celebration.id}
                label={celebration.label}
                selected={active.celebration === celebration.id}
                locked={isLocked(celebration)}
                onClick={() => {
                  pick('celebration', celebration);
                  // Locked or not, picking one shows what it looks like.
                  if (celebration.id !== 'none') setPreview((p) => ({ kind: celebration.id, run: (p?.run ?? 0) + 1 }));
                }}
              />
            ))}
          </div>
          <CelebrationPreview preview={preview} onDone={endPreview} />
        </div>
        {lockedNote('celebration')}
      </Group>
    </div>
  );
}

function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <fieldset className="grid gap-2">
      <legend className="mb-2 text-sm font-semibold">{title}</legend>
      {children}
    </fieldset>
  );
}

function OptionButton({ label, selected, locked, onClick, children }: { label: string; selected: boolean; locked: boolean; onClick: () => void; children?: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      aria-label={locked ? `${label} (supporters only)` : label}
      onClick={onClick}
      className={[
        'relative grid min-w-0 justify-items-center gap-1.5 rounded-box border-2 bg-base-100 px-2 py-2 text-xs font-medium transition-colors',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        selected ? 'border-primary' : 'border-base-300 hover:border-base-content/30',
      ].join(' ')}
    >
      {children}
      <span className="flex items-center gap-1">
        {locked && <Lock className="size-3 shrink-0 text-base-content/60" aria-hidden="true" />}
        <span className="truncate">{label}</span>
      </span>
    </button>
  );
}

/** A corner of a board in the theme: a given, a digit in pen and a note, on the theme's page colour. */
function ThemeSwatch({ theme }: { theme: ThemeOption }) {
  if (theme.daisyTheme === null) {
    // Follows the system: half light, half dark.
    return (
      <span className="flex h-12 w-full overflow-hidden rounded-sm" aria-hidden="true">
        <MiniBoard theme="light" className="w-1/2 rounded-none" />
        <MiniBoard theme="dark" className="w-1/2 rounded-none" />
      </span>
    );
  }
  return <MiniBoard theme={theme.daisyTheme} className="h-12 w-full" />;
}

function MiniBoard({ theme, className }: { theme: string; className: string }) {
  return (
    <span data-theme={theme} className={`flex items-center justify-center gap-1.5 rounded-sm bg-base-200 ${className}`} aria-hidden="true">
      <span className="grid grid-cols-2 overflow-hidden rounded-xs border-2 border-base-content bg-base-100 text-sm leading-none">
        <span className="grid size-5 place-items-center border-r border-b border-base-300 font-bold text-base-content">3</span>
        <span className="grid size-5 place-items-center border-b border-base-300 casual font-medium text-primary">1</span>
        <span className="grid size-5 place-items-center border-r border-base-300 casual text-[0.55rem] text-secondary">4</span>
        <span className="grid size-5 place-items-center casual font-medium text-error">5</span>
      </span>
    </span>
  );
}

function InkSwatch({ ink }: { ink: InkOption }) {
  const color = inkColor(ink);
  return (
    <span className={`grid size-9 place-items-center rounded-full border border-base-300 bg-base-200 casual text-xl font-medium ${color ? '' : 'text-primary'}`} style={color ? { color } : undefined} aria-hidden="true">
      5
    </span>
  );
}

/** Its own `data-digits`, Handwritten too, so it never shows the style picked for the page. */
function DigitSample({ style }: { style: DigitStyleId }) {
  return (
    <span data-digits={style} className="casual text-xl font-medium text-[color:var(--ink,var(--color-primary))]" aria-hidden="true">
      3 1 4
    </span>
  );
}

const DEMO = [3, 1, 4, 1, 5, 9, 2, 6, 5];

/** A tiny solved box, to show a celebration on. */
function CelebrationPreview({ preview, onDone }: { preview: { kind: CelebrationId; run: number } | null; onDone: () => void }) {
  const wave = preview?.kind === 'wave';
  return (
    <div className="grid justify-items-center gap-1">
      <div className="@container relative grid size-28 grid-cols-3 overflow-hidden rounded-sm border-2 border-base-content bg-base-100" aria-hidden="true">
        {DEMO.map((digit, i) => (
          <span key={i} className="grid place-items-center border-base-300 casual text-xl font-medium text-[color:var(--ink,var(--color-primary))] not-nth-[3n]:border-r not-nth-last-[-n+3]:border-b">
            {/* Keyed by the run, so the wave plays again on every click. */}
            <span key={wave ? preview.run : 'still'} className={wave ? 'inline-block motion-safe:animate-write-in' : undefined} style={wave ? { animationDelay: `${(Math.floor(i / 3) + (i % 3)) * 90}ms` } : undefined}>
              {digit}
            </span>
          </span>
        ))}
        {preview && preview.kind !== 'wave' && <Celebration key={preview.run} kind={preview.kind} onDone={onDone} />}
      </div>
      <span className="flex items-center gap-1 text-xs text-base-content/60">
        <Play className="size-3" aria-hidden="true" /> Preview
      </span>
    </div>
  );
}

function LockedNote({ label, canSupport }: { label: string; canSupport: boolean }) {
  return (
    <p role="status" className="flex items-start gap-2 text-sm text-base-content/80">
      <Coffee className="mt-0.5 size-4 shrink-0 text-secondary" aria-hidden="true" />
      <span>
        {label} is a thank-you for supporters.{' '}
        {canSupport && (
          <>
            A tip on Ko-fi unlocks it, with everything else here.{' '}
            <Link to="/me/support" className="link link-primary">
              How to support Pidoku
            </Link>
          </>
        )}
      </span>
    </p>
  );
}
