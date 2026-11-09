import { memo, useMemo, type CSSProperties } from 'react';
import { isGiven, isMistake, type GameState } from '../game/game';
import { EMPTY, findConflicts, isPeer } from '../game/rules';

interface BoardProps {
  state: GameState;
  showErrors: boolean;
  onSelect: (index: number) => void;
}

export function Board({ state, showErrors, onSelect }: BoardProps) {
  const { rules, values, notes, selected } = state;
  const conflicts = useMemo(() => findConflicts(rules, values), [rules, values]);
  const selectedValue = selected === null ? EMPTY : values[selected];

  return (
    <div
      className={[
        // --hl tints highlighted cells and --hl-line draws rings. Notes mode swaps ink blue for
        // violet so it's obvious a tap will pencil a note rather than write in pen.
        state.notesMode
          ? '[--hl:var(--color-secondary)] [--hl-line:var(--color-secondary)]'
          : '[--hl:var(--color-primary)] [--hl-line:var(--color-primary)]',
        '@container grid aspect-square w-full min-w-0 overflow-hidden rounded-sm border-3 border-base-content bg-base-100 select-none touch-manipulation',
        'sm:w-[min(100%,560px,calc(100vh_-_9rem))] sm:min-w-[280px]',
      ].join(' ')}
      role="grid"
      aria-label="Pidoku board"
      style={{ '--size': rules.size, gridTemplateColumns: `repeat(${rules.size}, 1fr)` } as CSSProperties}
    >
      {values.map((value, i) => {
        const row = Math.floor(i / rules.size);
        const col = i % rules.size;
        const error = showErrors && (conflicts.has(i) || isMistake(state, i));
        return (
          <Cell
            key={i}
            index={i}
            value={value}
            notes={notes[i]}
            given={isGiven(state, i)}
            selected={i === selected}
            peer={selected !== null && isPeer(rules, i, selected)}
            sameDigit={value !== EMPTY && value === selectedValue}
            highlightNote={selectedValue}
            error={error}
            boxRight={(col + 1) % rules.boxDim === 0 && col !== rules.size - 1}
            boxBottom={(row + 1) % rules.boxDim === 0 && row !== rules.size - 1}
            lastCol={col === rules.size - 1}
            lastRow={row === rules.size - 1}
            label={`Row ${row + 1}, column ${col + 1}`}
            onSelect={onSelect}
          />
        );
      })}
    </div>
  );
}

interface CellProps {
  index: number;
  value: number;
  notes: number;
  given: boolean;
  selected: boolean;
  peer: boolean;
  sameDigit: boolean;
  highlightNote: number;
  error: boolean;
  boxRight: boolean;
  boxBottom: boolean;
  lastCol: boolean;
  lastRow: boolean;
  label: string;
  onSelect: (index: number) => void;
}

const NOTE_SLOTS = [1, 2, 3, 4, 5, 6, 7, 8, 9];

const Cell = memo(function Cell(props: CellProps) {
  const { index, value, notes, given, selected, peer, sameDigit, highlightNote, error, boxRight, boxBottom, lastCol, lastRow, label, onSelect } = props;
  // Each property is picked in one place so no two utilities fight over it.
  const background = selected
    ? 'bg-(color:--hl)/35'
    : error
      ? 'bg-error/15'
      : sameDigit
        ? 'bg-(color:--hl)/20'
        : peer
          ? 'bg-(color:--hl)/8'
          : 'bg-base-100';
  const color = error ? 'text-error' : given ? 'text-base-content' : 'text-primary';
  const right = lastCol ? 'border-r-0' : boxRight ? 'border-r-2 border-r-base-content' : 'border-r border-r-base-300';
  const bottom = lastRow ? 'border-b-0' : boxBottom ? 'border-b-2 border-b-base-content' : 'border-b border-b-base-300';
  const classes = [
    'relative grid aspect-square cursor-pointer place-items-center p-0',
    'text-[length:calc(100cqi/var(--size)*0.55)]',
    'focus-visible:z-10 focus-visible:outline-2 focus-visible:-outline-offset-3 focus-visible:outline-(--hl-line)',
    // Hover is a neutral grey wash drawn as an inset shadow, so it shows where the pointer is without
    // looking like a selection and without replacing the peer/same-digit tint underneath.
    !selected && 'hover:shadow-[inset_0_0_0_100vmax_color-mix(in_oklab,var(--color-base-content)_9%,transparent)]',
    // Clues are printed; digits the player writes use the handwritten font.
    given ? 'font-bold' : 'casual font-medium',
    background,
    color,
    right,
    bottom,
  ]
    .filter(Boolean)
    .join(' ');

  const description = value !== EMPTY ? `${value}${given ? ', given' : ''}${error ? ', incorrect' : ''}` : 'empty';

  return (
    <button
      type="button"
      role="gridcell"
      className={classes}
      aria-label={`${label}: ${description}`}
      aria-selected={selected}
      onClick={() => onSelect(index)}
    >
      {value !== EMPTY ? (
        <span>{value}</span>
      ) : notes !== 0 ? (
        <span
          className="absolute inset-[6%] grid grid-cols-3 grid-rows-3 casual text-[length:calc(100cqi/var(--size)*0.24)] leading-none text-base-content/60"
          aria-hidden="true"
        >
          {NOTE_SLOTS.map((d) => (
            <span key={d} className={d === highlightNote ? 'grid place-items-center font-black text-primary' : 'grid place-items-center'}>
              {notes & (1 << d) ? d : ''}
            </span>
          ))}
        </span>
      ) : null}
    </button>
  );
});
