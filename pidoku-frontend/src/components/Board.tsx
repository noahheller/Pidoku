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
      className={state.notesMode ? 'board notes-mode' : 'board'}
      role="grid"
      aria-label="Pidoku board"
      style={{ '--size': rules.size } as CSSProperties}
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
  const classes = [
    'cell',
    given && 'given',
    selected && 'selected',
    !selected && sameDigit && 'same-digit',
    !selected && !sameDigit && peer && 'peer',
    error && 'error',
    boxRight && 'box-right',
    boxBottom && 'box-bottom',
    lastCol && 'last-col',
    lastRow && 'last-row',
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
        <span className="value">{value}</span>
      ) : notes !== 0 ? (
        <span className="notes" aria-hidden="true">
          {NOTE_SLOTS.map((d) => (
            <span key={d} className={d === highlightNote ? 'note match' : 'note'}>
              {notes & (1 << d) ? d : ''}
            </span>
          ))}
        </span>
      ) : null}
    </button>
  );
});
