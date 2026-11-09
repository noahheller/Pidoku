import type { GameAction, GameState } from '../game/game';

interface ControlsProps {
  state: GameState;
  dispatch: (action: GameAction) => void;
  showErrors: boolean;
  onToggleErrors: () => void;
}

export function Controls({ state, dispatch, showErrors, onToggleErrors }: ControlsProps) {
  const locked = state.completed;
  return (
    <div className="controls">
      <button type="button" onClick={() => dispatch({ type: 'undo' })} disabled={locked || state.undoStack.length === 0} title="Undo (Ctrl+Z)">
        ↶ Undo
      </button>
      <button type="button" onClick={() => dispatch({ type: 'redo' })} disabled={locked || state.redoStack.length === 0} title="Redo (Ctrl+Y)">
        ↷ Redo
      </button>
      <button type="button" onClick={() => dispatch({ type: 'erase' })} disabled={locked} title="Erase (Backspace)">
        ⌫ Erase
      </button>
      <button
        type="button"
        className={state.notesMode ? 'toggle on' : 'toggle'}
        aria-pressed={state.notesMode}
        onClick={() => dispatch({ type: 'toggleNotesMode' })}
        disabled={locked}
        title="Notes (N, or hold Shift while typing a digit)"
      >
        ✎ Notes {state.notesMode ? 'on' : 'off'}
      </button>
      <button type="button" className={showErrors ? 'toggle on' : 'toggle'} aria-pressed={showErrors} onClick={onToggleErrors} title="Highlight wrong digits">
        ⚠ Errors {showErrors ? 'on' : 'off'}
      </button>
      <button
        type="button"
        onClick={() => {
          if (confirm('Restart this puzzle? Your progress and time will be reset.')) dispatch({ type: 'restart' });
        }}
        title="Restart puzzle"
      >
        ⟲ Restart
      </button>
    </div>
  );
}
