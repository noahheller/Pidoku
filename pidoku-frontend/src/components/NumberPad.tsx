import { useMemo } from 'react';
import type { GameAction, GameState } from '../game/game';
import { remainingCounts } from '../game/rules';

interface NumberPadProps {
  state: GameState;
  dispatch: (action: GameAction) => void;
}

export function NumberPad({ state, dispatch }: NumberPadProps) {
  const { rules, values, notesMode } = state;
  const remaining = useMemo(() => remainingCounts(rules, values), [rules, values]);

  return (
    <div className={notesMode ? 'number-pad notes-mode' : 'number-pad'} aria-label="Digits">
      {rules.digits.map((d) => {
        const left = remaining.get(d) ?? 0;
        return (
          <button
            key={d}
            type="button"
            className={left <= 0 ? 'digit done' : 'digit'}
            onClick={() => dispatch({ type: 'input', digit: d })}
            aria-label={`${notesMode ? 'Note' : 'Enter'} ${d}, ${Math.max(left, 0)} left`}
          >
            <span className="digit-value">{d}</span>
            <span className="digit-left">{left > 0 ? left : '✓'}</span>
          </button>
        );
      })}
    </div>
  );
}
