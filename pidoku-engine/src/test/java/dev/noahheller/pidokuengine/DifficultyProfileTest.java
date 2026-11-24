package dev.noahheller.pidokuengine;

import dev.noahheller.pidokuengine.strategy.StrategyEnum;
import dev.noahheller.pidokuengine.strategy.Tier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DifficultyProfileTest {

    @Test
    void targetDefaultsToTheMiddleOfABoundedWindow() {
        DifficultyProfile expert = DifficultyProfile.expert();
        assertEquals((expert.minScore + expert.maxScore) / 2, expert.targetScore);
    }

    /** Regression: the midpoint of an open-ended window overflowed int, and later was ~1 billion. */
    @Test
    void openEndedWindowsDoNotAimAtInfinity() {
        DifficultyProfile open = new DifficultyProfile.Builder("Open").scoreRange(100, Integer.MAX_VALUE).build();
        assertEquals(100, open.targetScore);
        assertTrue(DifficultyProfile.fiendish().targetScore < 1000);
    }

    @Test
    void distanceToTargetIsSymmetric() {
        DifficultyProfile p = new DifficultyProfile.Builder("P").scoreRange(0, 100).targetScore(40).build();
        assertEquals(10, p.distanceToTarget(30));
        assertEquals(10, p.distanceToTarget(50));
        assertEquals(0, p.distanceToTarget(40));
    }

    @Test
    void premadeProfilesAreValid() {
        for (DifficultyProfile p : DifficultyProfile.premadeProfiles()) {
            assertTrue(p.targetScore >= p.minScore && p.targetScore <= p.maxScore, p.toString());
        }
    }

    /** The daily tiers' score windows are disjoint, so a harder tier always scores higher. */
    @Test
    void easyMediumAndHardScoresDoNotOverlap() {
        DifficultyProfile[] tiers = {DifficultyProfile.easy(), DifficultyProfile.medium(), DifficultyProfile.hard()};
        for (int i = 1; i < tiers.length; i++) {
            assertTrue(tiers[i - 1].maxScore < tiers[i].minScore, tiers[i - 1] + " overlaps " + tiers[i]);
        }
    }

    /**
     * Easy and Medium both use singles only, so clue count is what sets them apart: a 39-clue
     * Medium once played faster than that day's 40-clue Easy. (Hard is set apart by its strategy
     * floor, so it may share clue counts with Medium.)
     */
    @Test
    void mediumHasFewerCluesThanAnyEasy() {
        assertTrue(DifficultyProfile.medium().maxClues < DifficultyProfile.easy().minClues);
    }

    @Test
    void rejectsSettingsNoPuzzleCouldMeet() {
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder("x").scoreRange(50, 10).build());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder("x").clueRange(40, 30).build());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder("x").floor(Tier.EXPERT).ceiling(Tier.EASY).build());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder("x").scoreRange(10, 20).targetScore(30).build());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder("x").maxUses(StrategyEnum.X_WING, -1).build());
        assertThrows(IllegalArgumentException.class, () -> new DifficultyProfile.Builder(" ").build());
    }
}
