import { useState } from 'react';
import { Game } from './components/Game';
import { PuzzlePicker } from './components/PuzzlePicker';
import type { PuzzleDef } from './game/puzzleFile';
import { loadCustomPuzzles, saveCustomPuzzles } from './game/storage';
import { bundledPuzzles } from './puzzles';

function App() {
  const [custom, setCustom] = useState(loadCustomPuzzles);
  const [active, setActive] = useState<PuzzleDef | null>(null);

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
      onRemoveCustom={(id) => updateCustom(custom.filter((p) => p.id !== id))}
    />
  );
}

export default App;
