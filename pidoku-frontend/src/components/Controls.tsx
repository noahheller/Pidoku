import { Eraser, Lightbulb, Pencil, Redo2, RotateCcw, Sparkles, Undo2 } from 'lucide-react';
import type { ReactNode } from 'react';
import { canAutoNote, type GameAction, type GameState } from '../game/game';

interface ControlsProps {
  state: GameState;
  dispatch: (action: GameAction) => void;
  /** Offer Restart (the tutorial doesn't). */
  restart?: boolean;
  /** Offer Hint, for puzzles from the API (a hint needs the server); called when it's pressed. */
  onHint?: () => void;
  /** A hint is on its way. */
  hintBusy?: boolean;
}

const BUTTON =
  'btn max-sm:btn-sm border-base-300 bg-base-100 font-normal enabled:hover:border-primary enabled:hover:bg-primary/10 enabled:hover:text-primary';

// On phones Undo, Redo and Erase make a row of three, and the rest (Notes, then Auto notes, Hint and
// Restart when offered) fill the rows below: two share a row, three or more go in threes with a
// fourth across a row of its own. Wider, everything sits in two columns, with the last button
// across the bottom when the count is odd.
const THIRD = 'col-span-2 sm:col-span-1';

/** The span of each button after the first row, as laid out above. */
export function spans(count: number): string[] {
  const phone = count === 1 ? ['col-span-6'] : count === 2 ? ['col-span-3', 'col-span-3'] : ['col-span-2', 'col-span-2', 'col-span-2', 'col-span-6'].slice(0, count);
  // 3 in the first row, plus these: an odd total leaves the last one alone in its row.
  const lastAlone = (3 + count) % 2 === 1;
  return phone.map((span, i) => `${span} ${lastAlone && i === count - 1 ? 'sm:col-span-2' : 'sm:col-span-1'}`);
}

export function Controls({ state, dispatch, restart = true, onHint, hintBusy = false }: ControlsProps) {
  const locked = state.completed;
  const rest: ((span: string) => ReactNode)[] = [
    (span) => (
      <button
        key="notes"
        type="button"
        className={`${state.notesMode ? 'btn max-sm:btn-sm btn-secondary font-normal enabled:hover:brightness-110' : BUTTON} ${span}`}
        aria-pressed={state.notesMode}
        onClick={() => dispatch({ type: 'toggleNotesMode' })}
        disabled={locked}
        title="Notes (N, or hold Shift while typing a digit)"
      >
        <Pencil className="size-4 shrink-0" aria-hidden="true" /> Notes {state.notesMode ? 'on' : 'off'}
      </button>
    ),
  ];
  if (canAutoNote(state.puzzle)) {
    rest.push((span) => (
      <button
        key="auto"
        type="button"
        className={`${BUTTON} ${span}`}
        onClick={() => dispatch({ type: 'autoNotes' })}
        disabled={locked}
        title="Pencil every possible digit into the empty cells. Notes you've cleared stay cleared; Undo takes it back."
      >
        <Sparkles className="size-4 shrink-0" aria-hidden="true" /> Auto notes
      </button>
    ));
  }
  if (onHint) {
    rest.push((span) => (
      <button
        key="hint"
        type="button"
        className={`${BUTTON} ${span}`}
        onClick={onHint}
        disabled={locked || hintBusy}
        title="A hint for the next move, a step at a time. Each step adds time on the leaderboard: 10 s for where to look, 20 s for what to look for, 60 s for the answer."
      >
        {hintBusy ? <span className="loading loading-spinner loading-xs" aria-hidden="true" /> : <Lightbulb className="size-4 shrink-0" aria-hidden="true" />} Hint
      </button>
    ));
  }
  if (restart) {
    rest.push((span) => (
      <button
        key="restart"
        type="button"
        className={`${BUTTON} ${span}`}
        onClick={() => {
          const message = state.puzzle.dailyDate
            ? 'Clear the board and start this puzzle over? Your time, mistakes and hints so far carry on.'
            : 'Restart this puzzle? Your progress and time will be reset.';
          if (confirm(message)) dispatch({ type: 'restart' });
        }}
        title="Restart puzzle"
      >
        <RotateCcw className="size-4 shrink-0" aria-hidden="true" /> Restart
      </button>
    ));
  }
  const span = spans(rest.length);

  return (
    <div className="grid grid-cols-6 gap-2 sm:grid-cols-2">
      <button type="button" className={`${BUTTON} ${THIRD}`} onClick={() => dispatch({ type: 'undo' })} disabled={locked || state.undoStack.length === 0} title="Undo (Ctrl+Z)">
        <Undo2 className="size-4 shrink-0" aria-hidden="true" /> Undo
      </button>
      <button type="button" className={`${BUTTON} ${THIRD}`} onClick={() => dispatch({ type: 'redo' })} disabled={locked || state.redoStack.length === 0} title="Redo (Ctrl+Y)">
        <Redo2 className="size-4 shrink-0" aria-hidden="true" /> Redo
      </button>
      <button type="button" className={`${BUTTON} ${THIRD}`} onClick={() => dispatch({ type: 'erase' })} disabled={locked} title="Erase (Backspace)">
        <Eraser className="size-4 shrink-0" aria-hidden="true" /> Erase
      </button>
      {rest.map((render, i) => render(span[i]))}
    </div>
  );
}
