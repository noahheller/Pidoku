import { Check } from 'lucide-react';
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
    <div className="grid grid-cols-7 gap-1 sm:grid-cols-4 sm:gap-2" aria-label="Digits">
      {rules.digits.map((d) => {
        const left = remaining.get(d) ?? 0;
        const done = left <= 0;
        const valueStyle = done
          ? 'text-base-content/40'
          : notesMode
            ? 'text-xl font-normal text-base-content'
            : 'text-2xl font-semibold text-primary sm:text-3xl';
        return (
          <button
            key={d}
            type="button"
            className={[
              'btn h-auto aspect-[0.8] flex-col gap-0.5 bg-base-100 p-0 sm:aspect-[1.15]',
              notesMode
                ? 'border-dashed border-base-content/40 hover:bg-secondary/15'
                : 'border-base-300 hover:border-primary hover:bg-primary/10',
            ].join(' ')}
            onClick={() => dispatch({ type: 'input', digit: d })}
            aria-label={`${notesMode ? 'Note' : 'Enter'} ${d}, ${Math.max(left, 0)} left`}
          >
            <span className={`casual leading-none ${valueStyle}`}>{d}</span>
            <span className={`text-xs tabular-nums ${done ? 'text-success' : 'text-base-content/60'}`}>{done ? <Check className="size-3.5" strokeWidth={3} aria-hidden="true" /> : left}</span>
          </button>
        );
      })}
    </div>
  );
}
