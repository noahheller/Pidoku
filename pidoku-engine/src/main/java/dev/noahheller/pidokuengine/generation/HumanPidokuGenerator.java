package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.DifficultyProfile;
import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.searching.SolveTrace;
import dev.noahheller.pidokuengine.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class HumanPidokuGenerator implements PidokuGenerator {

    private static final int GREEDY_PASSES = 6;
    private static final int SWAP_ITERATIONS = 300;

    private final PidokuBacktrackSolver solver;
    private final HumanPidokuSolver humanSolver;
    private Random random;

    public HumanPidokuGenerator(PidokuRules rules, PidokuBacktrackSolver solver, long seed) {
        this.solver = solver;
        this.humanSolver = new HumanPidokuSolver(rules);
        this.random = new Random(seed);
    }

    @Override
    public PidokuPuzzle buildPuzzle(int[][] solution, DifficultyProfile profile) {
        random = new Random(random.nextLong());
        int size = solution.length;
        int total = size * size;

        int[][] bestPuzzle = null;
        int bestScore = Integer.MIN_VALUE;

        for (int pass = 0; pass < GREEDY_PASSES; pass++) {
            List<Integer> indices = new ArrayList<>(total);
            for (int i = 0; i < total; i++) indices.add(i);
            Collections.shuffle(indices, random);

            int[][] candidate = greedyReduce(ArrayUtils.copy(solution), indices, size, profile);
            if (candidate == null) continue;

            SolveTrace trace = humanSolver.solve(ArrayUtils.copy(candidate)).getSolveTrace();
            int clues = size*size - trace.getPlacementCount();
            if (profile.accepts(trace, clues) && trace.getTotalScore() > bestScore) {
                bestPuzzle = ArrayUtils.copy(candidate);
                bestScore = trace.getTotalScore();
            }
        }

        if (bestPuzzle == null) return null;

        bestPuzzle = swapRefine(bestPuzzle, solution, profile);

        return humanSolver.solve(ArrayUtils.copy(bestPuzzle));
    }

    /**
     * Removes cells from {@code puzzle} in the order given by {@code indices}.
     * A cell is removed only when:
     *   1. the puzzle remains uniquely solvable, AND
     *   2. the human solver can still solve it without guessing, AND
     *   3. the hardest strategy required does not exceed the profile's ceiling.
     * Returns the best intermediate puzzle that the profile accepted, or null
     * if the profile never accepted any intermediate state.
     */
    private int[][] greedyReduce(int[][] puzzle, List<Integer> indices, int size, DifficultyProfile profile) {
        int[][] bestAccepted = null;
        int bestScore = Integer.MIN_VALUE;

        for (int index : indices) {
            int row = index / size;
            int col = index % size;
            int saved = puzzle[row][col];
            if (saved == 0) continue;

            puzzle[row][col] = 0;

            // Hard constraint: must still have a unique solution.
            if (solver.countSolutions(puzzle) != 1) {
                puzzle[row][col] = saved;
                continue;
            }

            // Hard constraint: must still be solvable by human strategies alone.
            SolveTrace trace = humanSolver.solve(ArrayUtils.copy(puzzle)).getSolveTrace();
            if (trace.requiresGuessing) {
                puzzle[row][col] = saved;
                continue;
            }

            // Soft constraint: do not exceed the difficulty ceiling.
            // We still commit the removal even when the profile doesn't accept
            // the current state, because further removals may bring the score
            // back into the acceptable window.
            if (trace.getHardestStrategy() != null
                    && trace.getHardestStrategy().getTier().isHarderThan(profile.strategyTierCeiling)) {
                puzzle[row][col] = saved;
                continue;
            }

            int clues = size*size - trace.getPlacementCount();
            if (profile.accepts(trace, clues) && trace.getTotalScore() > bestScore) {
                bestAccepted = ArrayUtils.copy(puzzle);
                bestScore = trace.getTotalScore();
            }
        }

        return bestAccepted;
    }


    /**
     * Tries to improve {@code puzzle} by swapping a revealed cell for a hidden
     * cell (the hidden cell's value is taken from {@code solution}).  A swap is
     * kept whenever:
     *   1. the puzzle after the swap has a unique solution,
     *   2. the human solver can solve it without guessing,
     *   3. the hardest strategy stays within the ceiling, AND
     *   4. the profile accepts the result AND the score is at least as good.
     *
     * Swapping keeps the clue count constant, so this phase navigates the space
     * of puzzles with roughly the same sparsity as the greedy result while
     * searching for better difficulty structure.
     */
    private int[][] swapRefine(int[][] puzzle, int[][] solution, DifficultyProfile profile) {
        int size = puzzle.length;
        int[][] current = ArrayUtils.copy(puzzle);

        SolveTrace currentTrace = humanSolver.solve(ArrayUtils.copy(current)).getSolveTrace();
        int currentScore = currentTrace.getTotalScore();

        // Pre-build the two candidate lists so we only allocate them once.
        List<int[]> hiddenCells = new ArrayList<>();
        List<int[]> revealedCells = new ArrayList<>();

        for (int iter = 0; iter < SWAP_ITERATIONS; iter++) {
            int averageScore = (profile.minScore + profile.maxScore)/2;
            if (currentScore > averageScore){
                break;
            }
            hiddenCells.clear();
            revealedCells.clear();
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    if (current[r][c] == 0) {
                        hiddenCells.add(new int[]{r, c});
                    } else {
                        revealedCells.add(new int[]{r, c});
                    }
                }
            }

            if (hiddenCells.isEmpty() || revealedCells.isEmpty()) break;

            int[] toReveal = hiddenCells.get(random.nextInt(hiddenCells.size()));
            int[] toHide   = revealedCells.get(random.nextInt(revealedCells.size()));

            // Build the swapped candidate without permanently modifying current.
            int[][] candidate = ArrayUtils.copy(current);
            candidate[toReveal[0]][toReveal[1]] = solution[toReveal[0]][toReveal[1]];
            candidate[toHide[0]][toHide[1]]     = 0;

            if (solver.countSolutions(candidate) != 1) continue;

            SolveTrace trace = humanSolver.solve(ArrayUtils.copy(candidate)).getSolveTrace();
            if (trace.requiresGuessing) continue;

            if (trace.getHardestStrategy() != null
                    && trace.getHardestStrategy().getTier().isHarderThan(profile.strategyTierCeiling)) {
                continue;
            }

            int clues = size*size - trace.getPlacementCount();
            // Accept the swap if it keeps us in the profile and doesn't lower the score.
            if (profile.accepts(trace, clues) && trace.getTotalScore() >= currentScore) {
                current = candidate;
                currentScore = trace.getTotalScore();
            }
        }

        // double check still human solvable
        SolveTrace finalTrace = humanSolver.solve(ArrayUtils.copy(current)).getSolveTrace();
        int clues = size*size - finalTrace.getPlacementCount();
        return profile.accepts(finalTrace, clues) ? current : puzzle;
    }

}
