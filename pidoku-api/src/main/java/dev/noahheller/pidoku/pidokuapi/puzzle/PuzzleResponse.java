package dev.noahheller.pidoku.pidokuapi.puzzle;

/**
 * A puzzle as the frontend receives it. Grids are flat, row-major arrays with 0 for an empty cell,
 * matching the frontend's {@code PuzzleDef}.
 *
 * <p>The solution is included so the game can show mistakes as they happen. That makes cheating
 * possible, which is accepted for a game among friends (see docs/ROADMAP.md).
 *
 * @param hardestStrategy the engine's strategy name, e.g. {@code NAKED_PAIR}
 * @param dependency      average cells fillable with a single over the first 25 steps
 */
public record PuzzleResponse(
        long id,
        String shareCode,
        Difficulty difficulty,
        int size,
        int[] givens,
        int[] solution,
        int clueCount,
        int score,
        double dependency,
        String hardestStrategy
) {

    public static PuzzleResponse from(Puzzle puzzle) {
        return new PuzzleResponse(
                puzzle.getId(),
                puzzle.getShareCode(),
                puzzle.getDifficulty(),
                puzzle.getSize(),
                Grids.toCells(puzzle.getGivens()),
                Grids.toCells(puzzle.getSolution()),
                puzzle.getClueCount(),
                puzzle.getScore(),
                puzzle.getDependency(),
                puzzle.getHardestStrategy());
    }
}
