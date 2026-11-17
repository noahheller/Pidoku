import { useEffect, useState } from 'react';
import { sendPendingCompletions } from './api/completions';
import { Game } from './components/Game';
import { PuzzlePicker } from './components/PuzzlePicker';
import type { PuzzleDef } from './game/puzzleFile';
import { clearProgress, loadCustomPuzzles, saveCustomPuzzles } from './game/storage';
import { bundledPuzzles } from './puzzles';

function App() {
  const [custom, setCustom] = useState(loadCustomPuzzles);
  const [active, setActive] = useState<PuzzleDef | null>(null);

  // Solves the API couldn't be told about at the time (see api/completions.ts): try again now,
  // and whenever the connection comes back.
  useEffect(() => {
    void sendPendingCompletions();
    window.addEventListener('online', sendPendingCompletions);
    return () => window.removeEventListener('online', sendPendingCompletions);
  }, []);

  const updateCustom = (next: PuzzleDef[]) => {
    setCustom(next);
    saveCustomPuzzles(next);
  };

  if (active) {
    // key resets all game state when switching puzzles.
    return <Game key={active.id} puzzle={active} onExit={() => setActive(null)} />;
  }

  return (
    <PuzzlePicker
      bundled={bundledPuzzles}
      custom={custom}
      onPlay={setActive}
      onAddCustom={(added) => {
        const ids = new Set(added.map((p) => p.id));
        updateCustom([...custom.filter((p) => !ids.has(p.id)), ...added]);
      }}
      onRemoveCustom={(id) => {
        updateCustom(custom.filter((p) => p.id !== id));
        // Its saved progress goes with it, or it would sit in storage for good.
        clearProgress(id);
      }}
    />
  );
}

export default App;
