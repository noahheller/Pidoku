import { Eraser, Pencil, Redo2, RotateCcw, TriangleAlert, Undo2 } from 'lucide-react';
import type { GameAction, GameState } from '../game/game';

interface ControlsProps {
  state: GameState;
  dispatch: (action: GameAction) => void;
  showErrors: boolean;
  onToggleErrors: () => void;
}

const BUTTON =
  'btn max-sm:btn-sm border-base-300 bg-base-100 font-normal enabled:hover:border-primary enabled:hover:bg-primary/10 enabled:hover:text-primary';

export function Controls({ state, dispatch, showErrors, onToggleErrors }: ControlsProps) {
  const locked = state.completed;
  return (
    <div className="grid grid-cols-3 gap-2 sm:grid-cols-2">
      <button type="button" className={BUTTON} onClick={() => dispatch({ type: 'undo' })} disabled={locked || state.undoStack.length === 0} title="Undo (Ctrl+Z)">
        <Undo2 className="size-4 shrink-0" aria-hidden="true" /> Undo
      </button>
      <button type="button" className={BUTTON} onClick={() => dispatch({ type: 'redo' })} disabled={locked || state.redoStack.length === 0} title="Redo (Ctrl+Y)">
        <Redo2 className="size-4 shrink-0" aria-hidden="true" /> Redo
      </button>
      <button type="button" className={BUTTON} onClick={() => dispatch({ type: 'erase' })} disabled={locked} title="Erase (Backspace)">
        <Eraser className="size-4 shrink-0" aria-hidden="true" /> Erase
      </button>
      <button
        type="button"
        className={state.notesMode ? 'btn max-sm:btn-sm btn-secondary font-normal enabled:hover:brightness-110' : BUTTON}
        aria-pressed={state.notesMode}
        onClick={() => dispatch({ type: 'toggleNotesMode' })}
        disabled={locked}
        title="Notes (N, or hold Shift while typing a digit)"
      >
        <Pencil className="size-4 shrink-0" aria-hidden="true" /> Notes {state.notesMode ? 'on' : 'off'}
      </button>
      <button
        type="button"
        className={showErrors ? 'btn max-sm:btn-sm btn-primary font-normal hover:brightness-110' : BUTTON}
        aria-pressed={showErrors}
        onClick={onToggleErrors}
        title="Highlight wrong digits"
      >
        <TriangleAlert className="size-4 shrink-0" aria-hidden="true" /> Errors {showErrors ? 'on' : 'off'}
      </button>
      <button
        type="button"
        className={BUTTON}
        onClick={() => {
          if (confirm('Restart this puzzle? Your progress and time will be reset.')) dispatch({ type: 'restart' });
        }}
        title="Restart puzzle"
      >
        <RotateCcw className="size-4 shrink-0" aria-hidden="true" /> Restart
      </button>
    </div>
  );
}
