package dev.noahheller.pidokuengine.searching;

import dev.noahheller.pidokuengine.BoardPosition;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.generation.HumanPidokuSolver;
import dev.noahheller.pidokuengine.strategy.Move;
import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.noahheller.pidokuengine.PidokuTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DifficultyScorerTest {

    private final PidokuRules rules = rules();

    @Test
    void hiddenSinglesAreCheaperThanNakedSingles() {
        assertTrue(StrategyEnum.HIDDEN_SINGLE_BOX.getCost() < StrategyEnum.HIDDEN_SINGLE_LINE.getCost());
        assertTrue(StrategyEnum.HIDDEN_SINGLE_LINE.getCost() < StrategyEnum.NAKED_SINGLE.getCost());
    }

    @Test
    void strategiesAreDeclaredCheapestFirst() {
        StrategyEnum[] all = StrategyEnum.values();
        for (int i = 1; i < all.length; i++) {
            assertTrue(all[i - 1].getCost() <= all[i].getCost(), all[i - 1] + " should not cost more than " + all[i]);
        }
    }

    @Test
    void placingADigitThatAppearsTwiceCostsMore() {
        BoardPosition cell = new BoardPosition(0, 0);
        double once = DifficultyScorer.moveCost(StrategyEnum.HIDDEN_SINGLE_BOX, Move.placement(cell, 3), rules);
        double twice = DifficultyScorer.moveCost(StrategyEnum.HIDDEN_SINGLE_BOX, Move.placement(cell, 5), rules);
        assertEquals(StrategyEnum.HIDDEN_SINGLE_BOX.getCost(), once);
        assertEquals(once * DifficultyScorer.REPEATED_DIGIT_MULTIPLIER, twice);
    }

    @Test
    void dependencyAveragesOnlyTheFirst25Steps() {
        List<Integer> options = new ArrayList<>(Collections.nCopies(25, 4));
        options.addAll(Collections.nCopies(30, 20)); // a wide-open endgame shouldn't count
        assertEquals(4.0, DifficultyScorer.dependency(options), 1e-9);
        assertEquals(3.0, DifficultyScorer.dependency(List.of(2, 4)), 1e-9); // short solves use every step
        assertEquals(0.0, DifficultyScorer.dependency(List.of()), 1e-9);
    }

    @Test
    void scoreWeightsTheHardestStepTopStepsAndNarrowness() {
        List<Double> costs = new ArrayList<>(Collections.nCopies(20, 1.0));
        costs.add(10.0);
        // 3 × 10 (hardest) + (10 + 9 × 1) (top ten) + 0 (15+ options) + 0.2 × 21 placements = 53.2
        assertEquals(53, DifficultyScorer.score(costs, Collections.nCopies(21, 15), 50, 21));
        // 10 fewer options per step adds 3 × 10
        assertEquals(83, DifficultyScorer.score(costs, Collections.nCopies(21, 5), 50, 21));
        // more options than the reference never lowers the score
        assertEquals(53, DifficultyScorer.score(costs, Collections.nCopies(21, 30), 50, 21));
        assertEquals(0, DifficultyScorer.score(List.of(), List.of(), 0, 0));
    }

    @Test
    void nearlyFullBoardsAreNotPenalisedForHavingFewCellsLeft() {
        assertEquals(DifficultyScorer.DEPENDENCY_REFERENCE, DifficultyScorer.dependencyReference(51));
        assertEquals(4.0, DifficultyScorer.dependencyReference(8), 1e-9);
        // 8 empty cells, 4 options a step: as open as that board can be, so no narrowness term.
        List<Double> costs = Collections.nCopies(8, 1.0);
        assertEquals(DifficultyScorer.score(costs, Collections.nCopies(8, 30), 8, 8),
                DifficultyScorer.score(costs, Collections.nCopies(8, 4), 8, 8));
    }

    @Test
    void oneHardStepOutscoresLotsOfEasyOnes() {
        List<Double> longAndEasy = Collections.nCopies(60, 1.5);
        List<Double> shortWithOneHardStep = new ArrayList<>(Collections.nCopies(30, 1.5));
        shortWithOneHardStep.add(StrategyEnum.NAKED_PAIR.getCost());
        List<Integer> options = Collections.nCopies(60, 8);
        assertTrue(DifficultyScorer.score(shortWithOneHardStep, options, 50, 30)
                > DifficultyScorer.score(longAndEasy, options, 60, 60));
    }

    @Test
    void eliminationStepsCountAsZeroOptions() {
        // Find a puzzle that needs an elimination and check its options are recorded as 0.
        HumanPidokuSolver solver = new HumanPidokuSolver(rules);
        for (long seed = 0; seed < 200; seed++) {
            SolveTrace trace = solver.solve(punchHoles(fullSolution(seed), 55, seed)).getSolveTrace();
            for (SolveTrace.Step step : trace.getSteps()) {
                if (!step.isPlacement()) {
                    assertEquals(0, step.getOptions());
                    return;
                }
            }
        }
        fail("no puzzle needed an elimination");
    }

    @Test
    void sparserPuzzlesScoreHigher() {
        // Random holes don't always leave a logically solvable puzzle, so compare
        // only the seeds where both versions solve.
        HumanPidokuSolver solver = new HumanPidokuSolver(rules);
        int pairs = 0, denseTotal = 0, sparseTotal = 0;
        for (long seed = 0; seed < 40; seed++) {
            int[][] solution = fullSolution(seed);
            SolveTrace dense = solver.solve(punchHoles(solution, 20, seed)).getSolveTrace();
            SolveTrace sparse = solver.solve(punchHoles(solution, 35, seed)).getSolveTrace();
            if (!dense.solved || !sparse.solved) continue;
            pairs++;
            denseTotal += dense.getTotalScore();
            sparseTotal += sparse.getTotalScore();
        }
        assertTrue(pairs >= 5, "only " + pairs + " solvable pairs");
        assertTrue(sparseTotal > denseTotal, "sparse=" + sparseTotal + " dense=" + denseTotal);
    }

    @Test
    void endgameStepsAreNotBottlenecks() {
        // Eight holes: every step has few options, but fewer than 10 cells are ever empty.
        SolveTrace trace = new HumanPidokuSolver(rules).solve(punchHoles(fullSolution(22), 8, 22)).getSolveTrace();
        assertTrue(trace.solved);
        assertEquals(0, trace.getBottleneckSteps());
    }

    @Test
    void solverPrefersHiddenSinglesWhenBothAreAvailable() {
        // On a moderately open board most placements can be read several ways;
        // the solver should mostly take the box reading rather than the naked one.
        SolveTrace trace = new HumanPidokuSolver(rules).solve(punchHoles(fullSolution(23), 35, 23)).getSolveTrace();
        assertTrue(trace.solved);
        assertTrue(trace.getUseCount(StrategyEnum.HIDDEN_SINGLE_BOX) > trace.getUseCount(StrategyEnum.NAKED_SINGLE),
                trace.toString());
    }
}
