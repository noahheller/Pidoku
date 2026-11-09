import { hashGivens, parsePuzzleJson, type PuzzleDef } from '../game/puzzleFile';

/**
 * Bundled puzzles exported from pidoku-engine's Main. Drop another engine JSON file
 * into this folder (named NN-difficulty.json) and it shows up automatically.
 */
const files = import.meta.glob<unknown>('./*.json', { eager: true, import: 'default' });

export const bundledPuzzles: PuzzleDef[] = Object.entries(files)
  .sort(([a], [b]) => a.localeCompare(b))
  .map(([path, json]) => {
    const slug = path.replace(/^\.\//, '').replace(/\.json$/, '');
    const difficulty = capitalize(slug.replace(/^\d+-/, ''));
    const puzzle = parsePuzzleJson(json, { name: difficulty, difficulty });
    // The givens hash is part of the id so regenerating a file starts fresh progress
    // instead of showing the old puzzle's "Solved" badge on a new grid.
    return { ...puzzle, id: `bundled-${slug}-${hashGivens(puzzle.givens)}` };
  });

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
