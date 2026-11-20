import { Eraser, Pencil, Redo2, RotateCcw, Undo2 } from 'lucide-react';
import type { GameAction, GameState } from '../game/game';

interface ControlsProps {
  state: GameState;
  dispatch: (action: GameAction) => void;
}

const BUTTON =
  'btn max-sm:btn-sm border-base-300 bg-base-100 font-normal enabled:hover:border-primary enabled:hover:bg-primary/10 enabled:hover:text-primary';

// Five buttons: on phones a row of three (Undo, Redo, Erase) over a row of two (Notes, Restart);
// wider, two columns with Restart across the bottom.
const THIRD = 'col-span-2 sm:col-span-1';
const HALF = 'col-span-3 sm:col-span-1';

export function Controls({ state, dispatch }: ControlsProps) {
  const locked = state.completed;
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
      <button
        type="button"
        className={`${state.notesMode ? 'btn max-sm:btn-sm btn-secondary font-normal enabled:hover:brightness-110' : BUTTON} ${HALF}`}
        aria-pressed={state.notesMode}
        onClick={() => dispatch({ type: 'toggleNotesMode' })}
        disabled={locked}
        title="Notes (N, or hold Shift while typing a digit)"
      >
        <Pencil className="size-4 shrink-0" aria-hidden="true" /> Notes {state.notesMode ? 'on' : 'off'}
      </button>
      <button
        type="button"
        className={`${BUTTON} ${HALF} sm:col-span-2`}
        onClick={() => {
          const message = state.puzzle.dailyDate
            ? 'Clear the board and start this puzzle over? Your time and mistakes so far carry on.'
            : 'Restart this puzzle? Your progress and time will be reset.';
          if (confirm(message)) dispatch({ type: 'restart' });
        }}
        title="Restart puzzle"
      >
        <RotateCcw className="size-4 shrink-0" aria-hidden="true" /> Restart
      </button>
    </div>
  );
}
