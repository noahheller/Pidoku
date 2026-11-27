import { useEffect, useId, type CSSProperties } from 'react';
import { PI_STREAM, type CelebrationId } from '../game/cosmetics';

/** How long each celebration takes, after which it's taken off the page (see the animations in index.css). */
const DURATION_MS: Record<CelebrationId, number> = { none: 0, 'pi-cascade': 2400, 'pie-burst': 3500, wave: 2400 };

/**
 * What plays over the board when a puzzle is solved (a supporter's choice, see game/cosmetics.ts).
 * Purely decoration: it covers the board without taking clicks, it's hidden from screen readers,
 * and with reduced motion it doesn't show at all. The write-in wave is drawn by the board itself
 * (Board's `wave`), so it has nothing here.
 *
 * Its parent must be `relative` and a `@container`: sizes are in `cqi` of the board's width.
 */
export function Celebration({ kind, onDone }: { kind: CelebrationId; onDone: () => void }) {
  useEffect(() => {
    const timer = setTimeout(onDone, DURATION_MS[kind]);
    return () => clearTimeout(timer);
  }, [kind, onDone]);

  if (kind === 'none' || kind === 'wave') return null;
  return (
    <div className="pointer-events-none absolute inset-0 z-20 overflow-hidden motion-reduce:hidden" aria-hidden="true" data-testid="celebration">
      {kind === 'pi-cascade' ? <PiCascade /> : <PieBurst />}
    </div>
  );
}

const COLUMNS = 9;

/** π's digits falling down the board, one stream a column, each set off by a digit of π. */
function PiCascade() {
  return Array.from({ length: COLUMNS }, (_, col) => (
    <div
      key={col}
      className="absolute top-0 flex h-full animate-pi-fall flex-col items-center justify-around casual font-medium leading-none text-[color:var(--ink,var(--color-primary))] text-[length:calc(100cqi/9*0.5)]"
      style={{ left: `${(col / COLUMNS) * 100}%`, width: `${100 / COLUMNS}%`, animationDelay: `${Number(PI_STREAM[col]) * 70}ms` }}
    >
      {PI_STREAM.slice(col * COLUMNS, col * COLUMNS + COLUMNS)
        .split('')
        .map((digit, i) => (
          <span key={i}>{digit}</span>
        ))}
    </div>
  ));
}

/** Crust and cherry filling: always pie-coloured, whatever the theme. */
const PIE = { crust: '#e2a858', crustLight: '#f3cc85', crustDark: '#a8692a', filling: '#b3243b', fillingDark: '#6e1224' };
const SLICES = 8;
const CRUMBS = 14;

/**
 * A whole pie, seen from above, pops up in the middle of the board, wobbles, and bursts into
 * slices that fly outwards in a shower of crumbs. Every slice draws the same whole pie, clipped
 * to its own wedge, so together they're one pie with a π cut in its crust.
 */
function PieBurst() {
  // Only characters an SVG url(#...) reference takes as they are.
  const id = `pie${useId().replace(/[^\w-]/g, '')}`;
  return (
    <>
      <div className="absolute size-[60cqi] animate-pie-pop" style={{ left: 'calc(50% - 30cqi)', top: 'calc(50% - 30cqi)' }}>
        {Array.from({ length: SLICES }, (_, k) => (
          <PieSlice key={k} index={k} id={`${id}-${k}`} />
        ))}
      </div>
      {Array.from({ length: CRUMBS }, (_, i) => {
        const angle = ((i / CRUMBS) * 360 + Number(PI_STREAM[i]) * 7) * (Math.PI / 180);
        const distance = 26 + Number(PI_STREAM[i + 3]) * 2.5;
        const size = 1.2 + (Number(PI_STREAM[i + 7]) % 3) * 0.6;
        const style = {
          left: '50%',
          top: '50%',
          width: `${size}cqi`,
          height: `${size}cqi`,
          background: i % 4 === 0 ? PIE.filling : PIE.crust,
          '--dx': `${Math.cos(angle) * distance}cqi`,
          '--dy': `${Math.sin(angle) * distance}cqi`,
          '--spin': '0deg',
          animationDelay: `${PIE_BURST_AT_MS + (i % 3) * 60}ms`,
        } as CSSProperties;
        return <span key={i} className="absolute animate-pie-burst rounded-full" style={style} />;
      })}
    </>
  );
}

/** When the pie bursts, after popping up and wobbling (see pie-pop in index.css). */
const PIE_BURST_AT_MS = 1000;

function PieSlice({ index, id }: { index: number; id: string }) {
  const step = 360 / SLICES;
  // The wedge's middle, from straight up, clockwise; nudged by π so they don't fly like clockwork.
  const middle = index * step - 90 + step / 2;
  const rad = (deg: number) => (deg * Math.PI) / 180;
  const point = (deg: number, r: number) => `${(Math.cos(rad(deg)) * r).toFixed(2)} ${(Math.sin(rad(deg)) * r).toFixed(2)}`;
  const wedge = `M0 0 L${point(middle - step / 2, 52)} A52 52 0 0 1 ${point(middle + step / 2, 52)} Z`;
  // Only the straight edges, out to the crust's rim (47).
  const cut = `M${point(middle - step / 2, 47)} L0 0 L${point(middle + step / 2, 47)}`;
  const fly = rad(middle + (Number(PI_STREAM[index]) - 4.5) * 3);
  const distance = 48 + Number(PI_STREAM[index + 8]) * 2;
  const style = {
    // Turns about its own middle, not the pie's.
    transformOrigin: `${50 + Math.cos(rad(middle)) * 22}% ${50 + Math.sin(rad(middle)) * 22}%`,
    '--dx': `${Math.cos(fly) * distance}cqi`,
    '--dy': `${Math.sin(fly) * distance}cqi`,
    '--spin': `${(index % 2 ? 1 : -1) * (70 + Number(PI_STREAM[index + 4]) * 15)}deg`,
    animationDelay: `${PIE_BURST_AT_MS}ms`,
  } as CSSProperties;
  return (
    <svg viewBox="-50 -50 100 100" className="absolute inset-0 size-full animate-pie-slice overflow-visible" style={style}>
      <defs>
        <clipPath id={`${id}-wedge`}>
          <path d={wedge} />
        </clipPath>
        <radialGradient id={`${id}-crust`} cx="35%" cy="30%" r="75%">
          <stop offset="0%" stopColor={PIE.crustLight} />
          <stop offset="70%" stopColor={PIE.crust} />
          <stop offset="100%" stopColor={PIE.crustDark} />
        </radialGradient>
      </defs>
      <g clipPath={`url(#${id}-wedge)`}>
        {/* Filling, which shows at the cuts and through the vents. */}
        <circle r="47" fill={PIE.filling} />
        {/* Top crust, with a crimped edge. */}
        <circle r="43" fill={`url(#${id}-crust)`} />
        <circle r="44.5" fill="none" stroke={PIE.crustDark} strokeWidth="5" />
        <circle r="44.5" fill="none" stroke={PIE.crust} strokeWidth="4" strokeDasharray="4.2 2.8" strokeLinecap="round" />
        {/* Steam vents: a π in the middle and a slit in every slice. */}
        <text y="10" textAnchor="middle" fontSize="34" fontWeight="800" fontFamily="Georgia, 'Times New Roman', serif" fill={PIE.filling} stroke={PIE.fillingDark} strokeWidth="1">
          π
        </text>
        {Array.from({ length: SLICES }, (_, k) => {
          const at = k * step - 90 + step / 2;
          return <path key={k} d={`M${point(at, 22)} L${point(at, 32)}`} stroke={PIE.fillingDark} strokeWidth="2.4" strokeLinecap="round" />;
        })}
      </g>
      {/* The cut along this slice's edges, so the pie shows where it'll come apart. */}
      <path d={cut} fill="none" stroke={PIE.fillingDark} strokeWidth="1.2" strokeLinejoin="round" clipPath={`url(#${id}-wedge)`} />
    </svg>
  );
}
