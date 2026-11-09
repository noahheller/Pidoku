package dev.noahheller.pidokuengine.generation;

import dev.noahheller.pidokuengine.DifficultyProfile;
import dev.noahheller.pidokuengine.PidokuBuilder;
import dev.noahheller.pidokuengine.PidokuPuzzle;
import dev.noahheller.pidokuengine.PidokuRules;
import dev.noahheller.pidokuengine.strategy.Tier;
import org.junit.jupiter.api.Test;

import static dev.noahheller.pidokuengine.PidokuTestSupport.rules;
import static org.junit.jupiter.api.Assertions.*;

class HumanPidokuGeneratorTest {

    private static PidokuPuzzle generate(long seed, DifficultyProfile profile) {
        PidokuRules rules = rules();
        PidokuBacktrackSolver solver = new PidokuBacktrackSolver(rules, seed);
        return new PidokuBuilder(rules, solver, new HumanPidokuGenerator(rules, solver, seed)).generate(profile);
    }

    private static DifficultyProfile easyAimingAt(int target) {
        return new DifficultyProfile.Builder("Easy@" + target)
                .scoreRange(35, 70).targetScore(target).clueRange(30, 45)
                .ceiling(Tier.BEGINNER).floor(Tier.BEGINNER).build();
    }

    /**
     * Regression: every stage used to keep the highest score it could, so puzzles
     * landed at the hard edge of their window. With the same seed, the candidates are
     * identical and only the choice between them depends on the target, so aiming low
     * can never produce a harder puzzle than aiming high.
     */
    @Test
    void puzzlesMoveTowardsTheTargetScore() {
        boolean anyDifference = false;
        for (long seed = 1; seed <= 4; seed++) {
            int low = generate(seed, easyAimingAt(35)).getSolveTrace().getTotalScore();
            int high = generate(seed, easyAimingAt(70)).getSolveTrace().getTotalScore();
            assertTrue(low <= high, "seed " + seed + ": low target scored " + low + ", high target " + high);
            anyDifference |= low < high;
        }
        assertTrue(anyDifference, "target score never changed the result");
    }

    @Test
    void defaultTargetIsTheMiddleOfTheWindow() {
        PidokuPuzzle puzzle = generate(3, DifficultyProfile.easy());
        DifficultyProfile easy = DifficultyProfile.easy();
        int score = puzzle.getSolveTrace().getTotalScore();
        // Not exact (the search is limited), but well clear of the hard edge it used to hit.
        assertTrue(Math.abs(score - easy.targetScore) <= (easy.maxScore - easy.minScore) / 4,
                "score " + score + " is far from target " + easy.targetScore);
    }
}
