import type { GameAction } from './game';
import { isPlayable, type Rules } from './rules';

/** The game's keyboard shortcuts: arrows move, digits write (Shift for a note), N toggles notes. */
export function actionForKey(event: KeyboardEvent, rules: Rules): GameAction | null {
  const mod = event.ctrlKey || event.metaKey;
  if (mod && event.key.toLowerCase() === 'z') return { type: event.shiftKey ? 'redo' : 'undo' };
  if (mod && event.key.toLowerCase() === 'y') return { type: 'redo' };
  if (mod || event.altKey) return null;

  switch (event.key) {
    case 'ArrowUp':
      return { type: 'move', dRow: -1, dCol: 0 };
    case 'ArrowDown':
      return { type: 'move', dRow: 1, dCol: 0 };
    case 'ArrowLeft':
      return { type: 'move', dRow: 0, dCol: -1 };
    case 'ArrowRight':
      return { type: 'move', dRow: 0, dCol: 1 };
    case 'Backspace':
    case 'Delete':
      return { type: 'erase' };
    case 'n':
    case 'N':
      return { type: 'toggleNotesMode' };
    case 'Escape':
      return { type: 'select', index: null };
  }

  // event.code, not event.key, so Shift+1 still reads as 1 rather than "!".
  const match = /^(?:Digit|Numpad)(\d)$/.exec(event.code);
  if (match) {
    const digit = Number(match[1]);
    if (isPlayable(rules, digit)) return { type: 'input', digit, asNote: event.shiftKey ? true : undefined };
  }
  return null;
}
